# schedule — 3레이어 통합 달력

채용공고 데이터 / 채용 전형 / 개인 일정의 3가지 카테고리를 단일 달력 UI에 통합 표시.

## 엔티티

### ScheduleEvent

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK |
| `userId` | `Long` (val) | NOT NULL, INDEX, FK ON DELETE CASCADE |
| `applicationId` | `Long?` (var) | nullable, INDEX (FK 안 검 — Application 삭제는 Service에서 정리) |
| `category` | `ScheduleCategory` | NOT NULL |
| `title` | `String` | NOT NULL |
| `startAt` | `Instant` | NOT NULL, INDEX (월별 조회용) |
| `endAt` | `Instant?` | |

```kotlin
enum class ScheduleCategory {
    JOB_POSTING,            // 채용공고 데이터 (마감/접수 시작/설명회 등) — DB 저장, applicationId 필수
    APPLICATION_PROCESS,    // 채용 전형 (면접/코테/발표)
    PERSONAL,               // 개인 일정
}
```

## 카테고리별 데이터 출처

| 카테고리 | DB 저장 여부 | 출처 | `applicationId` |
|---|---|---|---|
| `JOB_POSTING` | ✅ `ScheduleEvent` | 사용자 수동 등록 또는 메일에서 추출 (`Application.deadlineAt` 변경 시 Service가 동기화) | NOT NULL |
| `APPLICATION_PROCESS` | ✅ `ScheduleEvent` | 사용자 수동 등록 또는 메일에서 추출 | NOT NULL |
| `PERSONAL` | ✅ `ScheduleEvent` | 사용자 수동 등록 | null |

## 채용공고 일정 저장 정책

`JOB_POSTING`은 일반 `ScheduleEvent` row로 DB에 저장된다. 마감일은 그중 가장 흔한 형태이고, 이 외에도 접수 시작일·설명회 등 채용공고 관련 일정을 같은 카테고리에 담는다.

`Application.deadlineAt`은 칸반 측 마감 정렬·알림 폴링용 컬럼으로 별도 유지되며, 달력 표시용 `JOB_POSTING` row와는 **Service 계층에서 단일 트랜잭션으로 동기화**한다.

- `Application` 생성 시 `deadlineAt`이 있으면 매칭 `JOB_POSTING` row 1건도 함께 생성
- `Application.deadlineAt` 변경 → 매칭 row의 `startAt` 갱신
- `Application.deadlineAt` null로 변경 → 매칭 row 삭제
- `Application` 삭제 → 연결된 모든 JOB_POSTING row도 삭제 (`schedule/`의 `deleteByApplicationId`가 처리)

```kotlin
fun getCalendar(userId: Long, from: Instant, to: Instant): List<CalendarEventResponse> =
    scheduleEventRepository
        .findByUserIdAndStartAtBetween(userId, from, to)
        .map { it.toResponse() }
```

## 삭제 정책

| 트리거 | 결과 |
|---|---|
| `User` 삭제 | DB FK CASCADE로 자동 |
| `Application` 삭제 | **Service 명시 정리** — Redis 알림 큐 비움 + DB hard delete |
| 사용자 일정 직접 삭제 | hard delete + 알림 큐 정리 |

```kotlin
@Transactional
fun deleteByApplicationId(applicationId: Long) {
    val events = scheduleEventRepository.findByApplicationId(applicationId)
    scheduleEventRepository.deleteAll(events)
    notificationQueue.removeByApplicationId(applicationId)
}
```

`application/` 도메인의 `deleteApplication` 안에서 호출.

## 외부 캘린더 export

iCalendar(`.ics`) **일회성 다운로드** 모델. 외부 시스템과의 양방향 동기화는 하지 않는다.

```
사용자 → GET /api/schedule/events/{id}/export
  → Service가 ScheduleEvent → iCalendar(VEVENT) 직렬화
  → Content-Type: text/calendar 로 .ics 파일 응답
  → 사용자는 받은 파일을 Google Calendar / Outlook에 직접 import
```

DB에 외부 시스템 ID를 추적하지 않으므로 같은 이벤트를 여러 번 export 해도 매번 새 `.ics`가 생성된다. 이후 `ScheduleEvent`가 수정·삭제돼도 이미 import 된 외부 일정에는 반영되지 않음 — 양방향 동기화가 필요해지면 별도 도메인으로 분리해서 다룬다.

## 알림 연동

`startAt` 기준 D-3 / D-1 / 당일 알림을 Redis Sorted Set에 적재 (`notification/` 도메인이 Phase 2에서 책임).

- `ScheduleEvent` 생성/수정 → 큐 갱신
- `ScheduleEvent` 삭제 → 큐에서 제거
- `Application.deadlineAt` 변경 → 큐 갱신 (이건 `application/` 도메인 책임)

## 도메인 메서드

```kotlin
@Entity
@Table(name = "schedule_events")
class ScheduleEvent(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "application_id")
    var applicationId: Long?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: ScheduleCategory,

    @Column(nullable = false)
    var title: String,

    @Column(name = "start_at", nullable = false)
    var startAt: Instant,

    var endAt: Instant? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun reschedule(startAt: Instant, endAt: Instant?) {
        this.startAt = startAt
        this.endAt = endAt
    }
}
```

## 권장 인덱스

- `schedule_events(user_id, start_at)` — 월별 달력 조회 (p95 ≤ 300ms 목표)
- `schedule_events(application_id)` — 카드 삭제 시 일괄 조회
- `schedule_events(user_id, category, start_at)` — 레이어 필터링

## API 요약

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/schedule/calendar?from=...&to=...&category=...` | 달력 조회 (마감일 변환 + ScheduleEvent 통합) |
| POST | `/api/schedule/events` | 일정 등록 (`APPLICATION_PROCESS` 또는 `PERSONAL`) |
| PATCH | `/api/schedule/events/{id}` | 일정 수정 |
| DELETE | `/api/schedule/events/{id}` | 일정 삭제 |
| GET | `/api/schedule/events/{id}/export` | iCalendar(`.ics`) 파일 다운로드 (외부 캘린더 import용) |

## 검증 규칙 (Service)

- `category = JOB_POSTING` 또는 `APPLICATION_PROCESS`인 경우 `applicationId` 필수 (없으면 `INVALID_INPUT`)
- `category = PERSONAL`인 경우 `applicationId`는 null
- 모든 카테고리에서 `applicationId`는 동일 `userId` 소유의 Application이어야 함 (IDOR 방지)
- `endAt < startAt`이면 `INVALID_INPUT`
