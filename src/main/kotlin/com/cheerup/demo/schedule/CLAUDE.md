# schedule — 3레이어 통합 달력

채용공고 마감 / 채용 전형 / 개인 일정의 3가지 카테고리를 단일 달력 UI에 통합 표시.

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
| `allDay` | `Boolean` | NOT NULL, default false |
| `location` | `String?` | |
| `memo` | `String?` (TEXT) | |
| `externalCalendarId` | `String?` | Google/Outlook export 후 외부 ID |

```kotlin
enum class ScheduleCategory {
    JOB_POSTING,            // 채용공고 마감 — 저장 안 함, Application.deadlineAt에서 변환
    APPLICATION_PROCESS,    // 채용 전형 (면접/코테/발표)
    PERSONAL,               // 개인 일정
}
```

## 카테고리별 데이터 출처

| 카테고리 | DB 저장 여부 | 출처 | `applicationId` |
|---|---|---|---|
| `JOB_POSTING` | ❌ 저장 안 함 | `Application.deadlineAt` | 응답 시 매핑 |
| `APPLICATION_PROCESS` | ✅ `ScheduleEvent` | 사용자 수동 등록 또는 메일에서 추출 | NOT NULL |
| `PERSONAL` | ✅ `ScheduleEvent` | 사용자 수동 등록 | null |

## 마감일이 저장 안 되는 이유

`Application.deadlineAt`이 마감일 **원본**.
달력 API는 사용자 카드들의 `deadlineAt`을 읽어 `JOB_POSTING` 이벤트로 **변환만** 해서 응답.

중복 저장 시 동기화 실패(`Application.deadlineAt` 수정 ≠ 달력 row 수정) 위험을 피하기 위함.

```kotlin
fun getCalendar(userId: Long, from: Instant, to: Instant): List<CalendarEventResponse> {
    val deadlines = applicationRepository
        .findWithDeadlineBetween(userId, from, to)
        .map { it.toJobPostingEvent() }                     // 변환만, 저장 X

    val events = scheduleEventRepository
        .findByUserIdAndStartAtBetween(userId, from, to)
        .map { it.toResponse() }

    return deadlines + events
}
```

## 삭제 정책

| 트리거 | 결과 |
|---|---|
| `User` 삭제 | DB FK CASCADE로 자동 |
| `Application` 삭제 | **Service 명시 정리** — 외부 캘린더 export 취소 + Redis 알림 큐 비움 + DB hard delete |
| 사용자 일정 직접 삭제 | hard delete + 외부 캘린더 정리 + 알림 큐 정리 |

```kotlin
@Transactional
fun deleteByApplicationId(applicationId: Long) {
    val events = scheduleEventRepository.findByApplicationId(applicationId)
    events.forEach { calendarExporter.cancelExternalExport(it) }
    scheduleEventRepository.deleteAll(events)
    notificationQueue.removeByApplicationId(applicationId)
}
```

`application/` 도메인의 `deleteApplication` 안에서 호출.

## 외부 캘린더 export

```
사용자 → POST /api/schedule/events/{id}/export?provider=google
  → Google Calendar Insert API 호출
  → externalCalendarId 저장
  → 이후 일정 수정/삭제 시 외부 캘린더에도 반영
```

`externalCalendarId`가 채워져 있으면 export 됨. 사용자가 export 취소 시 외부 캘린더에서도 삭제.

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

    @Column(nullable = false)
    var allDay: Boolean = false,

    var location: String? = null,

    @Column(columnDefinition = "TEXT")
    var memo: String? = null,

    @Column(name = "external_calendar_id")
    var externalCalendarId: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun reschedule(startAt: Instant, endAt: Instant?) {
        this.startAt = startAt
        this.endAt = endAt
    }

    fun linkExternal(externalId: String) { this.externalCalendarId = externalId }
    fun unlinkExternal() { this.externalCalendarId = null }
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
| POST | `/api/schedule/events/{id}/export` | 외부 캘린더로 export (provider 지정) |

## 검증 규칙 (Service)

- `category = JOB_POSTING`인 row는 직접 생성/수정 금지 (`INVALID_INPUT`)
- `category = APPLICATION_PROCESS`인 경우 `applicationId` 필수
- `category = PERSONAL`인 경우 `applicationId`는 null
- `endAt < startAt`이면 `INVALID_INPUT`
