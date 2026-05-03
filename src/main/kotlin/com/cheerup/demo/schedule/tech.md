# schedule 도메인 — 기술 스펙 (tech.md)

이 문서는 `schedule/` 도메인(3레이어 통합 달력)의 **구현 진입 직전 설계 명세**다. 도메인 모델·정책의 *왜*는 [`CLAUDE.md`](./CLAUDE.md)에 있다. 본 문서는 그것을 받아 *어떻게 동작하는지* — API 계약, 플로우, 검증, 트랜잭션, 테스트 — 를 정의한다.

> 깊이: **설계 수준**. 필드 시그니처·메서드 본문은 구현 단계에서 결정하되, 본 문서가 정한 입출력 형태·플로우·에러 코드는 그대로 따른다.

---

## 1. Scope

### In Scope (이 도메인이 책임짐)
- `ScheduleEvent` 단일 엔티티의 영속화·조회·변경
- 월별 달력 조회 — 3가지 카테고리(`JOB_POSTING` / `APPLICATION_PROCESS` / `PERSONAL`) 통합 응답
- 일정 직접 등록 (`APPLICATION_PROCESS` / `PERSONAL`) 및 메일 추출 결과 수락 시 등록
- `Application.deadlineAt` 변경에 대응하는 `JOB_POSTING` row 동기화 (`application/`이 호출하는 내부 인터페이스 제공)
- `Application` 삭제 시 연결된 `ScheduleEvent` 일괄 정리 + 알림 큐 비움
- iCalendar(`.ics`) 일회성 다운로드 export

### Out of Scope (다른 도메인이 처리)
| 책임 | 위임 대상 |
|---|---|
| 알림 발송·스케줄링 | `notification/` (Redis Sorted Set, D-3/D-1/0d) |
| 외부 캘린더 양방향 동기화 | **미지원** (v1은 단방향 .ics 다운로드만) |
| 채용 메일 → 일정 추출 | `ai/`, `mail/` (`Suggestion` 형태로 도착, 사용자 수락 시 본 도메인이 `create` 호출) |
| `Application.deadlineAt`의 원본 보유 | `application/` (칸반 측이 source of truth) |
| 사용자 컨텍스트 / JWT | `global/jwt`, `@CurrentUser` |

### 비기능 요구
- 월별 달력 조회: **p95 ≤ 300ms** (사용자당 이벤트 수 기대치 ≤ 200)
- 모든 엔드포인트 IDOR 방지 — 요청 `userId`와 리소스 소유자 일치 검증 필수
- 본 도메인 모든 row는 hard delete (회고/통계 보존 사용 사례 없음)

---

## 2. Data Model 개요

세부 필드는 `CLAUDE.md` §엔티티 참조. 본 절은 **저장소 계층의 의사결정**만 다룬다.

### 테이블/제약 요약

| 테이블 | PK | 유니크 | 외래키 (DB 레벨) |
|---|---|---|---|
| `schedule_events` | `id` | — (Service에서 §2.2 규칙 강제) | `user_id → users(id) ON DELETE CASCADE`<br>`application_id`는 **FK 미설정** (Application 삭제는 Service에서 명시 정리) |

**`application_id`에 DB FK를 걸지 않는 이유**: `Application` 삭제 시 외부 시스템(알림 큐) 정리가 함께 필요하므로 어차피 Service 코드를 거친다. DB CASCADE가 추가하는 보호는 알림 큐 누락 위험과 맞바꾸는 셈이라 포기.

### 인덱스

| 인덱스 | 사용처 |
|---|---|
| `schedule_events(user_id, start_at)` | 월별 달력 조회 (p95 ≤ 300ms 목표) |
| `schedule_events(user_id, category, start_at)` | 카테고리 필터 + 기간 조건 |
| `schedule_events(application_id)` | `Application` 삭제 시 일괄 조회, `deadlineAt` 동기화 시 lookup |

### 2.2 v1 sync 규칙 — JOB_POSTING은 Application당 1개

`Application.deadlineAt`(칸반 원본)과 `JOB_POSTING` `ScheduleEvent`(달력 표시)를 단일 트랜잭션으로 동기화한다. v1은 **한 Application당 최대 1개의 JOB_POSTING row**만 허용해 sync 식별 문제를 단순화한다.

- 동기화 대상 row 식별: `(application_id, category=JOB_POSTING)` 단일행 lookup
- 사용자/AI가 같은 Application에 두 번째 `JOB_POSTING`을 만들려 하면 **`SCHEDULE_DUPLICATE_JOB_POSTING` (409)**
- 추가 채용공고 일정(설명회·접수 시작 등)을 v1에 표현하려면 카테고리를 `APPLICATION_PROCESS`로 등록 (UX 가이드)
- v2 후보(§12): `kind` 컬럼(DEADLINE/EXPLAINER/...)으로 다중 JOB_POSTING 허용

### Soft delete

전 도메인 hard delete. `BaseEntity.deletedAt`은 두되 미사용.

---

## 3. API 계약

공통 규약(인증·응답·오류·시간)은 `application/tech.md` §3와 동일.

### 3.1 달력 조회

#### `GET /api/schedule/calendar`

**Query**
| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `from` | ISO-8601 UTC | ✅ | 조회 구간 시작 (포함) |
| `to` | ISO-8601 UTC | ✅ | 조회 구간 끝 (포함) |
| `category` | CSV(`JOB_POSTING,APPLICATION_PROCESS,PERSONAL`) | ❌ | 다중 선택, 미지정 시 전체 |

**검증**
- `to - from ≤ 100일` (`INVALID_INPUT`) — 한 번에 너무 넓은 조회 차단
- `from < to`

**응답 200**
```json
{
  "data": {
    "events": [
      {
        "id": 501,
        "applicationId": 101,
        "category": "JOB_POSTING",
        "title": "토스 백엔드 마감",
        "startAt": "2026-05-10T14:00:00Z",
        "endAt": null
      },
      {
        "id": 502,
        "applicationId": 101,
        "category": "APPLICATION_PROCESS",
        "title": "1차 면접",
        "startAt": "2026-05-15T07:00:00Z",
        "endAt": "2026-05-15T08:00:00Z"
      },
      {
        "id": 600,
        "applicationId": null,
        "category": "PERSONAL",
        "title": "스터디",
        "startAt": "2026-05-12T10:00:00Z",
        "endAt": null
      }
    ]
  },
  "meta": { ... }
}
```

**구현 노트**
- 단일 쿼리: `findByUserIdAndStartAtBetween` + 카테고리 IN 필터. `application_id`는 그대로 노출 — 프론트가 클릭 시 칸반으로 점프하는 데 사용.
- 카테고리 색상/아이콘은 프론트 책임. 서버는 enum 문자열만 응답.
- 정렬: `startAt ASC, id ASC` (안정 정렬).

---

### 3.2 일정 CRUD

#### `POST /api/schedule/events`
일정 등록.

**요청**
```json
{
  "category": "APPLICATION_PROCESS",
  "applicationId": 101,
  "title": "1차 면접",
  "startAt": "2026-05-15T07:00:00Z",
  "endAt": "2026-05-15T08:00:00Z"
}
```

**검증**
- `category`: enum 필수
- `applicationId`:
  - `JOB_POSTING` / `APPLICATION_PROCESS` → **필수**, 요청자 소유 Application이어야 함
  - `PERSONAL` → null 강제 (값이 있으면 `INVALID_INPUT`)
- `title`: NotBlank, 1..200자
- `startAt`: 필수
- `endAt`: 옵셔널, `startAt`보다 같거나 이후 (`INVALID_INPUT`)
- `JOB_POSTING` 등록 시 동일 `applicationId`에 이미 `JOB_POSTING`이 있으면 **`SCHEDULE_DUPLICATE_JOB_POSTING` (409)**

**응답 201**: 등록된 단일 `ScheduleEventResponse`.

**부수 효과**: `notification/`에 알림 등록 위임 (`startAt` 기준 D-3/D-1/0d).

---

#### `PATCH /api/schedule/events/{id}`
부분 수정.

**요청 (모든 필드 옵셔널)**
```json
{
  "title": "1차 면접 (재일정)",
  "startAt": "2026-05-16T07:00:00Z",
  "endAt": "2026-05-16T08:00:00Z"
}
```

**검증**
- 소유자 = 요청자, 아니면 **404 `SCHEDULE_NOT_FOUND`**
- `category` / `applicationId`는 변경 불가 (요청에 있어도 무시 — 카테고리 전환은 삭제 후 재등록)
- `endAt < startAt`이면 `INVALID_INPUT`
- `JOB_POSTING` 카테고리 row를 직접 PATCH 하는 경우 `Application.deadlineAt`과 어긋날 수 있음 — v1 정책: 직접 PATCH 허용하되, `application/` 측 sync가 다음 deadlineAt 변경 때 덮어씀 (단순화). v2에서 잠금 정책 도입 검토.

**부수 효과**: `startAt` 변경 시 알림 큐 갱신.

---

#### `DELETE /api/schedule/events/{id}`

**플로우**
1. `findByIdAndUserId` → 없으면 404
2. **`category=JOB_POSTING`이고 매칭 `Application.deadlineAt`이 여전히 NOT NULL이면 거절** → `SCHEDULE_JOB_POSTING_LOCKED` (409). 사용자에게 "칸반에서 마감일을 먼저 비워주세요" 안내. (직접 삭제 허용 시 다음 sync에서 다시 생성되므로 무한 핑퐁 방지)
3. `notificationQueue.removeByEventId(id)` — Redis ZREM
4. `scheduleEventRepository.delete(event)`

**응답 204**.

---

### 3.3 export

#### `GET /api/schedule/events/{id}/export`
iCalendar(`.ics`) 다운로드.

**응답 200**
```
Content-Type: text/calendar; charset=UTF-8
Content-Disposition: attachment; filename="cheerup-event-{id}.ics"

BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//cheerup//ko//
BEGIN:VEVENT
UID:cheerup-event-{id}@cheerup.app
DTSTAMP:20260501T030000Z
DTSTART:20260515T070000Z
DTEND:20260515T080000Z
SUMMARY:1차 면접
END:VEVENT
END:VCALENDAR
```

**구현 노트**
- `BiweeklyJS` 같은 라이브러리 대신 직접 문자열 빌더로 작성 — VEVENT 1개 수준이라 의존성 추가 불필요
- TZ는 항상 UTC(`Z` suffix)로 작성, 사용자 단말이 변환 책임
- 사용자별 일괄 export(`GET /api/schedule/export.ics`)는 v2 후보(§12)

---

## 4. 핵심 플로우

### 4.1 달력 조회

```
프론트: GET /api/schedule/calendar?from=...&to=...&category=JOB_POSTING,APPLICATION_PROCESS
   ↓
Controller: @PreAuthorize + @CurrentUser → ScheduleQueryService.find(userId, from, to, categories)
   ↓
Service (@Transactional(readOnly=true))
  └─ scheduleEventRepository.findByUserIdAndCategoryInAndStartAtBetween(...)
       (인덱스: schedule_events(user_id, category, start_at))
   ↓
List<ScheduleEvent> → map { it.toResponse() }
   ↓
ApiResponse.success({ events })
```

100일 가드 + 인덱스로 p95 ≤ 300ms 도달 가능.

### 4.2 Application.deadlineAt 동기화

`application/`의 `ApplicationService`가 변경 시점마다 `schedule/`의 인터페이스를 호출한다. **본 도메인이 인터페이스 시그니처를 정의하고**, application/이 의존한다 (반대 방향 X — 칸반은 schedule을 모르고 schedule만 칸반 모델을 안다).

```kotlin
// schedule/service/ScheduleSyncService.kt (interface)
interface ScheduleSyncService {
    fun syncApplicationDeadline(userId: Long, applicationId: Long, companyName: String, deadlineAt: Instant?)
    fun deleteByApplicationId(userId: Long, applicationId: Long)
}
```

```
ApplicationService.update / create 안에서:

if (deadlineAt 필드가 변경됨)
  scheduleSyncService.syncApplicationDeadline(userId, applicationId, companyName, newDeadlineAt)

ScheduleSyncService 구현 (단일 트랜잭션, application/의 @Transactional 안에서 동작)
  ├─ 기존 row = repo.findByApplicationIdAndCategory(applicationId, JOB_POSTING)
  ├─ when {
  │     deadlineAt == null && 기존 == null → no-op
  │     deadlineAt == null && 기존 != null → repo.delete(기존), notificationQueue.removeByEventId(기존.id)
  │     deadlineAt != null && 기존 == null → save(new ScheduleEvent(category=JOB_POSTING, title="${companyName} 채용 마감", startAt=deadlineAt)), notificationQueue.enqueue(...)
  │     deadlineAt != null && 기존 != null → 기존.startAt = deadlineAt; 기존.title = "${companyName} 채용 마감"; notificationQueue.update(기존.id, deadlineAt)
  │  }
  └─ 종료
```

**왜 application/이 schedule/을 부르고 그 반대가 아닌가**: deadline의 source는 칸반이고, 달력은 미러다. 변경 트리거를 source가 발신해야 sync가 lossless.

### 4.3 Application 삭제 → ScheduleEvent 정리

```
ApplicationService.delete 안에서:
  ├─ scheduleSyncService.deleteByApplicationId(userId, applicationId)
  │     ├─ events = repo.findByApplicationId(applicationId)
  │     ├─ events.forEach { notificationQueue.removeByEventId(it.id) }
  │     └─ repo.deleteAll(events)
  └─ applicationRepository.delete(...)
```

본 도메인은 외부 캘린더 추적이 없으므로 `cancelExternalExport` 호출 불필요.

### 4.4 사용자 직접 일정 등록

```
POST /api/schedule/events  body: { category: APPLICATION_PROCESS, applicationId: 101, ... }
   ↓
Service (@Transactional)
  ├─ category 따라 applicationId 검증
  │     - JOB_POSTING/APPLICATION_PROCESS: applicationRepository.existsByIdAndUserId(applicationId, userId) 검증
  │     - PERSONAL: applicationId가 null인지 검증
  ├─ JOB_POSTING이면 동일 applicationId로 이미 존재하는지 확인 → 있으면 SCHEDULE_DUPLICATE_JOB_POSTING
  ├─ ScheduleEvent 생성·save
  └─ notificationQueue.enqueue(eventId, startAt - 3d, 1d, 0d)
   ↓
201
```

`existsByIdAndUserId`는 `applications(user_id, id)` PK + index로 빠름. 본 도메인이 `application/` repository에 의존하지만, **read-only 쿼리만** 호출 (도메인 메서드 호출 금지).

### 4.5 iCalendar export

```
GET /api/schedule/events/501/export
   ↓
Service: findByIdAndUserId → 없으면 404
   ↓
ICalendarBuilder.build(event) → String
   ↓
Controller: ResponseEntity.ok()
  .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
  .header("Content-Disposition", "attachment; filename=...")
  .body(content)
```

별도 트랜잭션 없음 (read-only).

---

## 5. 검증 규칙 매트릭스

| 필드 | 규칙 | 위반 시 코드 |
|---|---|---|
| `from`, `to` | 필수, ISO-8601, `to - from ≤ 100일`, `from < to` | `INVALID_INPUT` |
| `category` (enum) | 정의된 값 | `INVALID_INPUT` |
| `category` (POST) | JOB_POSTING/APPLICATION_PROCESS → applicationId 필수, PERSONAL → null | `INVALID_INPUT` |
| `applicationId` | 요청자 소유 | `APPLICATION_NOT_FOUND` (404) |
| `title` | NotBlank, 1..200 | `INVALID_INPUT` |
| `startAt` | 필수 | `INVALID_INPUT` |
| `endAt` | nullable, `endAt ≥ startAt` | `INVALID_INPUT` |
| JOB_POSTING 중복 | 동일 applicationId의 JOB_POSTING 존재 시 거절 | `SCHEDULE_DUPLICATE_JOB_POSTING` (409) |
| JOB_POSTING DELETE | `Application.deadlineAt`이 NOT NULL인 동안은 거절 | `SCHEDULE_JOB_POSTING_LOCKED` (409) |

---

## 6. ErrorCode

추가될 코드 (`global/exception/ErrorCode.kt`):

```kotlin
SCHEDULE_NOT_FOUND(NOT_FOUND, "SCHEDULE_NOT_FOUND", "일정을 찾을 수 없습니다"),
SCHEDULE_DUPLICATE_JOB_POSTING(CONFLICT, "SCHEDULE_DUPLICATE_JOB_POSTING", "해당 지원에 이미 채용공고 일정이 존재합니다"),
SCHEDULE_JOB_POSTING_LOCKED(CONFLICT, "SCHEDULE_JOB_POSTING_LOCKED", "지원 카드의 마감일을 먼저 비워주세요"),
```

`APPLICATION_NOT_FOUND`, `INVALID_INPUT`, `UNAUTHORIZED`는 `application/`·`global/`에 이미 정의됨을 가정하고 재사용.

---

## 7. 트랜잭션 경계

- **모든 변경 메서드는 `@Transactional`** — Hibernate dirty checking, 알림 큐 호출이 한 단위
- **클래스 레벨 `@Transactional(readOnly = true)`**, 변경 메서드만 위에서 재선언
- **`ScheduleSyncService` 메서드는 application/의 트랜잭션에 참여(propagation = REQUIRED 기본값)** — 칸반 변경과 달력 sync가 한 트랜잭션, 한쪽 실패 시 모두 롤백
- **Redis 호출은 트랜잭션 안에서 동기 호출**. 실패 시 전체 롤백 (가용성보다 정합성 우선) — `application/tech.md` §7과 동일 정책. Redis 1ms 수준이라 락 시간 영향 미미

---

## 8. 동시성 고려

| 시나리오 | 위험 | 대응 |
|---|---|---|
| `Application.deadlineAt` 두 탭 동시 변경 | 두 sync 호출이 같은 row를 update — 마지막 쓰기 승, 알림 큐는 마지막 값으로 정리 | 허용. 비즈니스 영향 없음 (마지막 값이 정답) |
| 같은 Application에 두 사용자가 JOB_POSTING POST 동시 | 둘 다 통과 후 두 row 생성 | Service 검사가 race이지만, v1은 `applicationId` 소유자 단일이라 동시 발생 불가 |
| 메일 추출 결과 수락과 사용자 직접 등록 충돌 | 같은 Application JOB_POSTING 두 row | `SCHEDULE_DUPLICATE_JOB_POSTING`로 두 번째가 거절. 사용자 UX는 "기존 일정을 수정하시겠습니까?" 안내 (프론트 책임) |
| 일정 PATCH와 Application 삭제 동시 | PATCH 후 사라짐 | 데이터 손실 없음 — Application 삭제가 마지막 작업이므로 트랜잭션 격리로 충분 |

`SELECT FOR UPDATE`는 v1에서 도입하지 않음. 사용자당 동시 요청 빈도가 낮음.

---

## 9. 보안 체크리스트

- [ ] 모든 Repository 메서드에 `userId` 조건 포함 — Spec 작성 시 grep
- [ ] `applicationId`로 Application을 조회할 때도 동일 `userId` 검증 (IDOR 회귀)
- [ ] 모든 Controller에 `@PreAuthorize("isAuthenticated()")` + `@CurrentUser`
- [ ] 다른 사용자 리소스 조회는 항상 **404** (존재 노출 방지)
- [ ] iCalendar 응답은 사용자 소유 이벤트만 반환 — `findByIdAndUserId` 강제
- [ ] `title`은 사용자 입력 그대로 저장. iCalendar export 시 줄바꿈/`,`/`;`은 RFC 5545 escape (`\\`, `\,`, `\;`)
- [ ] `SCHEDULE_JOB_POSTING_LOCKED` 분기는 `Application.deadlineAt`을 읽으므로 `application/` repository 의존 — read-only 쿼리에 한정

---

## 10. 테스트 시나리오

### 10.1 단위 (MockK)

- `getCalendar`: from-to > 100일 → `INVALID_INPUT`
- `getCalendar`: 카테고리 필터링 — 단일/다중/미지정 모두 검증
- `create` JOB_POSTING: 동일 applicationId 기존 row 있으면 `SCHEDULE_DUPLICATE_JOB_POSTING`
- `create` APPLICATION_PROCESS: applicationId가 다른 사용자 소유 → `APPLICATION_NOT_FOUND`
- `create` PERSONAL: applicationId 값이 있으면 `INVALID_INPUT`
- `update`: category/applicationId는 무시되는지 (요청 보내도 변경 안 됨)
- `delete` JOB_POSTING: Application.deadlineAt이 NOT NULL → `SCHEDULE_JOB_POSTING_LOCKED`
- `delete` APPLICATION_PROCESS: notificationQueue.removeByEventId 호출 후 row 삭제
- `syncApplicationDeadline` 4분기 모두 검증 (null↔null, null→Δ, Δ→null, Δ→Δ')
- `deleteByApplicationId`: 모든 카테고리 row 정리 + 알림 큐 비움

### 10.2 통합 (Testcontainers)

- 사용자 A, B 각각 일정 등록 → A가 B의 일정 GET → 빈 응답
- Application 생성(deadlineAt 설정) → JOB_POSTING row 자동 생성 검증
- Application.deadlineAt 변경 → JOB_POSTING.startAt 동기 변경
- Application.deadlineAt = null → JOB_POSTING row 사라짐, 알림 큐도 비워짐
- Application 삭제 → 모든 카테고리 ScheduleEvent 사라짐
- 100일 초과 캘린더 GET → 400
- iCalendar export → Content-Type `text/calendar`, VEVENT 1개 포함, RFC 5545 escape 적용 (title에 `,` 있는 케이스)
- IDOR 회귀: 다른 사용자 event PATCH/DELETE/export → 404

### 10.3 부하 (선택)
- 사용자당 이벤트 200개 + 월별 GET p95 측정 (목표 ≤ 300ms)

---

## 11. 마이그레이션

```
V8__create_schedule_events.sql
V9__index_schedule_events_user_start.sql
V10__index_schedule_events_user_category_start.sql
V11__index_schedule_events_application.sql
```

`application/`의 V2~V7 이후 적용. `application_id`는 FK 미설정.

---

## 12. 미해결 / v2 후보

| 항목 | 현재 결정 | v2 검토 사유 |
|---|---|---|
| 한 Application당 다중 `JOB_POSTING` (설명회·접수 시작 등) | v1 1개 제한 | 채용공고가 단계 많은 회사에서 UX 요구 발생 시 — `kind` 컬럼(DEADLINE/EXPLAINER/...) 추가 |
| 일정 일괄 export(`GET /api/schedule/export.ics?from=...&to=...`) | v1 단일 이벤트만 | 사용자가 분기 단위로 캘린더 import 하려고 할 때 |
| 양방향 외부 캘린더 동기화 | 미지원 | 사용자가 외부 캘린더에서 수정한 내용을 끌어오는 요구가 누적되면 별도 도메인으로 분리 |
| `JOB_POSTING` 직접 PATCH 시 칸반 deadlineAt 자동 갱신 | 무시 (다음 sync에서 덮어씀) | 사용자가 달력에서 마감을 옮기는 UX 피드백이 모이면 양방향 동기화 도입 |
| 반복 일정(주간 스터디 등) | 미지원 | 사용자 요청 누적 시 RRULE 또는 별도 RecurrenceRule 엔티티 |
| 알림 시간 사용자 정의 (D-7 등) | D-3/D-1/0d 고정 | 사용자 설정 페이지 추가 시 |
| `SCHEDULE_JOB_POSTING_LOCKED` 분기 제거 | v1 도입 | 칸반/달력 단방향 강제가 UX 마찰 만들 시 |

---

## 13. 구현 순서 제안

1. `ScheduleEvent` 엔티티 + Repository + 마이그레이션 + Testcontainers 적용 검증
2. `ScheduleQueryService.getCalendar` (read-only) + 단위 테스트 — 가장 간단, 인덱스 검증 일찍
3. `ScheduleEventCommandService.create / update / delete` + 카테고리별 검증 + 단위 테스트
4. `ScheduleSyncService` 인터페이스 + 구현 (4분기) + 단위 테스트
5. `application/` 측에서 `ScheduleSyncService.syncApplicationDeadline` 호출 wire-up (set/update/delete 흐름)
6. `notificationQueue` stub 인터페이스로 `enqueue` / `update` / `removeByEventId` / `removeByApplicationId` 정의 (실 구현은 `notification/` PR)
7. iCalendar export 컨트롤러 + 단위 테스트 (RFC 5545 escape 케이스)
8. 통합 테스트(Testcontainers) — IDOR 회귀, sync 4분기, JOB_POSTING_LOCKED, export Content-Type
9. CLAUDE.md의 도메인 요약·엔드포인트 표 갱신, 본 tech.md의 §12 미해결 항목 재정리
