# notification 도메인 — 기술 스펙 (tech.md)

이 문서는 `notification/` 도메인(웹 사이트 내부 알림함)의 구현 진입 직전 설계 명세다. v1 알림은 외부 푸시가 아니라 사용자가 서비스 화면 안에서 확인하는 알림함이다.

> 핵심 결정: Redis Sorted Set은 "언제 알림을 만들지"를 관리하는 예약 큐로만 사용하고, 사용자가 조회·읽음 처리하는 알림은 DB `notifications` 테이블에 저장한다.

---

## 1. 책임 범위

### In Scope

- `Application.deadlineAt`, `ScheduleEvent.startAt` 기준 D-3/D-1/당일 알림 예약
- Redis Sorted Set 기반 due polling
- due 시점이 된 예약 항목을 DB `notifications` row로 생성
- 웹 알림 목록 조회, 읽지 않은 알림 수 조회, 단건/전체 읽음 처리 API
- Application/Schedule 변경·삭제 시 예약 알림 enqueue/update/remove 연동

### Out of Scope

| 책임 | v1 결정 |
|---|---|
| 이메일 알림 | 미지원 |
| FCM/mobile push | 미지원 |
| 브라우저 push notification | 미지원 |
| WebSocket/SSE 실시간 push | 미지원 |
| 사용자별 알림 시간 커스터마이징 | 미지원. D-3/D-1/당일 고정 |
| 알림 보관 기간 자동 삭제 | v1 미지원. 운영 데이터가 쌓인 뒤 정책 결정 |

프론트는 v1에서 polling 또는 화면 진입 시 API 조회로 알림을 가져간다.

---

## 2. 데이터 모델

### 2.1 Entity

```kotlin
enum class NotificationSourceType {
    APPLICATION,
    SCHEDULE_EVENT,
}

enum class NotificationRemindType {
    D_MINUS_3,
    D_MINUS_1,
    D_DAY,
}

class Notification(
    var id: Long? = null,
    val userId: Long,
    val sourceType: NotificationSourceType,
    val sourceId: Long,
    val remindType: NotificationRemindType,
    var title: String,
    var message: String,
    val scheduledAt: Instant,
    var readAt: Instant? = null,
) : BaseEntity()
```

### 2.2 Table

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT PK | 알림 ID |
| `user_id` | BIGINT NOT NULL | 알림 소유자 |
| `source_type` | VARCHAR(32) NOT NULL | `APPLICATION`, `SCHEDULE_EVENT` |
| `source_id` | BIGINT NOT NULL | 원본 리소스 ID |
| `remind_type` | VARCHAR(32) NOT NULL | `D_MINUS_3`, `D_MINUS_1`, `D_DAY` |
| `title` | VARCHAR(200) NOT NULL | 알림 제목 |
| `message` | VARCHAR(500) NOT NULL | 알림 본문 |
| `scheduled_at` | DATETIME(6) NOT NULL | 알림 기준 시각(UTC) |
| `read_at` | DATETIME(6) NULL | 읽음 처리 시각 |
| `created_at` | DATETIME(6) NOT NULL | 생성 시각 |
| `updated_at` | DATETIME(6) NOT NULL | 수정 시각 |

### 2.3 Constraints And Indexes

| 제약/인덱스 | 목적 |
|---|---|
| UNIQUE `(user_id, source_type, source_id, remind_type, scheduled_at)` | 같은 기준 시각의 scheduler 재시도/중복 enqueue만 막고, deadline/startAt 변경 후 새 기준 시각 알림은 다시 생성 |
| INDEX `(user_id, id)` | id keyset 기반 알림 목록 최신순 조회 |
| INDEX `(user_id, read_at, id)` | unread 목록·카운트 조회 |
| INDEX `(source_type, source_id)` | 원본 리소스 삭제 시 정리 후보 조회 |

`source_id`에는 DB FK를 걸지 않는다. Application/Schedule 삭제 시 Redis 큐와 DB 알림 정리가 함께 필요하므로 Service에서 명시 정리한다.

---

## 3. Redis 예약 큐

### 3.1 Sorted Set

| 항목 | 값 |
|---|---|
| key | `notifications:due` |
| score | `triggerAt.toEpochMilli()` |
| value | `{sourceType}:{sourceId}:{remindType}:{userId}` |

예시:

```text
APPLICATION:101:D_MINUS_1:99
SCHEDULE_EVENT:501:D_DAY:99
```

### 3.2 Trigger 계산

| `remindType` | `triggerAt` |
|---|---|
| `D_MINUS_3` | `scheduledAt - 3 days` |
| `D_MINUS_1` | `scheduledAt - 1 day` |
| `D_DAY` | `scheduledAt` |

- `scheduledAt`은 Application deadline 또는 ScheduleEvent startAt이다.
- `triggerAt <= now`인 예약은 Redis에 등록하지 않는다.
- 과거 마감일을 허용하는 Application 정책은 유지하되, 이미 지난 reminder는 생성하지 않는다.

### 3.3 Queue Port

```kotlin
interface NotificationQueue {
    fun enqueueApplicationDeadline(userId: Long, applicationId: Long, deadlineAt: Instant)
    fun updateApplicationDeadline(userId: Long, applicationId: Long, deadlineAt: Instant)
    fun removeByApplicationId(userId: Long, applicationId: Long)

    fun enqueueScheduleEvent(userId: Long, eventId: Long, startAt: Instant)
    fun updateScheduleEvent(userId: Long, eventId: Long, startAt: Instant)
    fun removeByEventId(userId: Long, eventId: Long)
}
```

`update*`는 같은 source의 기존 Redis item을 모두 제거한 뒤 새 D-3/D-1/D-DAY item을 등록한다. remove는 DB 알림 삭제가 아니라 아직 발송되지 않은 예약 큐 제거만 담당한다.

---

## 4. API

모든 API는 인증된 사용자만 호출한다. 구현 전까지 기존 패턴에 맞춰 `@AssignUserId`를 사용하고, JWT/AOP 정착 후 `@CurrentUser`로 이동한다.

### `GET /api/notifications`

알림 목록 조회.

**Query**

| 이름 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `unreadOnly` | Boolean | `false` | `true`면 읽지 않은 알림만 조회 |
| `limit` | Int | `20` | 1..50 |
| `cursor` | Long? | null | 이전 페이지 마지막 notification id |

**응답**

```json
{
  "notifications": [
    {
      "id": 1001,
      "sourceType": "APPLICATION",
      "sourceId": 101,
      "remindType": "D_MINUS_1",
      "title": "토스 채용 마감 D-1",
      "message": "토스 채용 마감이 1일 남았습니다.",
      "scheduledAt": "2026-05-10T14:00:00Z",
      "readAt": null,
      "createdAt": "2026-05-09T14:00:00Z"
    }
  ],
  "nextCursor": 1001
}
```

정렬은 `id DESC` 기준이다. cursor는 이전 페이지 마지막 notification id이며, 다음 페이지는 `id < cursor` 조건으로 조회한다.

### `GET /api/notifications/unread-count`

읽지 않은 알림 수 조회.

```json
{
  "count": 3
}
```

### `PATCH /api/notifications/{id}/read`

단건 읽음 처리.

**플로우**

1. `findByIdAndUserId(id, userId)` → 없으면 `NOTIFICATION_NOT_FOUND`
2. `readAt == null`이면 현재 UTC `Instant`로 갱신
3. 이미 읽은 알림이면 변경 없이 현재 상태 반환

### `PATCH /api/notifications/read-all`

사용자 전체 읽음 처리.

**응답**

```json
{
  "updatedCount": 5
}
```

---

## 5. 핵심 플로우

### 5.1 Application 마감 생성·변경

```text
ApplicationService.create
  ├─ Application 저장
  ├─ scheduleSyncService.syncApplicationDeadline(...)
  └─ notificationQueue.enqueueApplicationDeadline(userId, applicationId, deadlineAt)

ApplicationService.update
  ├─ Application 저장 또는 deadlineAt 변경
  ├─ scheduleSyncService.syncApplicationDeadline(...)
  └─ notificationQueue.updateApplicationDeadline(userId, applicationId, deadlineAt)
```

- `deadlineAt == null`로 비우는 흐름이 도입되면 `removeByApplicationId`를 호출한다.
- 현재 `UpdateApplicationRequest`는 null을 "변경 없음"으로 해석하므로 deadline 제거 API/DTO 정책은 별도 작업이다.

### 5.2 Application 삭제

```text
ApplicationService.delete
  ├─ findByIdAndUserId
  ├─ notificationQueue.removeByApplicationId(userId, applicationId)
  ├─ scheduleSyncService.deleteByApplicationId(userId, applicationId)
  └─ applicationRepository.delete(application)
```

Redis 예약 큐는 DB cascade로 정리되지 않으므로 삭제 Service에서 명시 제거한다.

### 5.3 ScheduleEvent 생성·변경·삭제

```text
ScheduleEventCommandService.create
  ├─ ScheduleEvent 저장
  └─ notificationQueue.enqueueScheduleEvent(userId, eventId, startAt)

ScheduleEventCommandService.update
  ├─ startAt 변경 여부 확인
  └─ 변경 시 notificationQueue.updateScheduleEvent(userId, eventId, nextStartAt)

ScheduleEventCommandService.delete
  ├─ notificationQueue.removeByEventId(userId, eventId)
  └─ scheduleEventRepository.delete(event)
```

`DefaultScheduleSyncService`가 Application deadline으로 자동 생성·갱신하는 `JOB_POSTING` event는 달력 표시용 mirror다. 같은 마감에 대해 `APPLICATION` source 알림이 이미 등록되므로, 자동 `JOB_POSTING` 생성·갱신 시 `SCHEDULE_EVENT` 알림을 추가로 등록하지 않는다.

단, 과거 버전이나 정책 변경으로 남아 있을 수 있는 stale queue를 없애기 위해 자동 `JOB_POSTING` 삭제 시에는 `notificationQueue.removeByEventId`를 호출한다.

### 5.4 Due Polling

1분마다 실행한다.

```text
NotificationDueScheduler
  ├─ now = clock.instant()
  ├─ ZRANGEBYSCORE notifications:due -inf now LIMIT 0 batchSize
  ├─ 각 value 파싱
  ├─ 원본 리소스 조회 또는 최소 정보 구성
  ├─ notifications INSERT
  │    └─ unique 충돌이면 이미 생성된 알림으로 보고 무시
  └─ DB 저장 성공 후 ZREM
```

- 저장 실패 시 Redis item을 제거하지 않는다. 다음 tick에서 재시도한다.
- source 리소스가 이미 삭제되어 없으면 Redis item을 제거하고 DB 알림은 만들지 않는다.
- batch size는 v1 기본 100으로 시작한다.

---

## 6. 메시지 생성 정책

알림 제목과 본문은 서버에서 생성한다.

| source | remindType | 예시 |
|---|---|---|
| APPLICATION | D_MINUS_3 | `토스 채용 마감 D-3` / `토스 채용 마감이 3일 남았습니다.` |
| APPLICATION | D_MINUS_1 | `토스 채용 마감 D-1` / `토스 채용 마감이 1일 남았습니다.` |
| APPLICATION | D_DAY | `토스 채용 마감일` / `오늘 토스 채용 마감일입니다.` |
| SCHEDULE_EVENT | D_MINUS_3 | `{title} D-3` / `{title} 일정이 3일 남았습니다.` |
| SCHEDULE_EVENT | D_MINUS_1 | `{title} D-1` / `{title} 일정이 1일 남았습니다.` |
| SCHEDULE_EVENT | D_DAY | `{title}` / `오늘 예정된 일정입니다.` |

Application 알림은 companyName을 읽어 메시지를 만든다. 원본이 삭제된 경우에는 알림을 만들지 않는다.

---

## 7. 트랜잭션 경계

- Application/Schedule 변경 메서드 안에서 Redis queue 호출을 동기 수행한다.
- Redis 실패 시 예외를 던지고 전체 트랜잭션을 롤백한다. v1은 가용성보다 정합성을 우선한다.
- scheduler는 Redis item 1개 처리와 DB insert를 작은 트랜잭션으로 묶는다.
- DB 저장 성공 후 Redis 제거가 실패하면 다음 tick에서 재처리될 수 있다. unique 제약으로 DB 중복 생성은 막는다.

---

## 8. ErrorCode

추가할 코드:

```kotlin
NOTIFICATION_NOT_FOUND(NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다")
```

검증 실패는 기존 `INVALID_INPUT`, 인증 실패는 기존 `UNAUTHORIZED`를 사용한다.

---

## 9. 테스트 시나리오

### 9.1 단위 테스트

- D-3/D-1/D-DAY trigger 계산
- `triggerAt <= now`인 reminder는 enqueue하지 않음
- `enqueueApplicationDeadline`은 APPLICATION item을 새로 등록
- `updateApplicationDeadline`은 기존 APPLICATION item 제거 후 새 item 등록
- `updateScheduleEvent`는 기존 SCHEDULE_EVENT item 제거 후 새 item 등록
- `removeByApplicationId`, `removeByEventId`가 source에 맞는 Redis value를 제거
- scheduler가 due item을 DB 알림으로 만들고 Redis에서 제거
- scheduler 재시도 또는 중복 item 처리 시 unique 제약으로 알림 중복 생성 없음
- 알림 단건 읽음, 전체 읽음 처리
- 다른 사용자 알림 read 요청은 `NOTIFICATION_NOT_FOUND`

### 9.2 서비스 연동 테스트

- Application 생성 시 deadlineAt이 있으면 queue enqueue/update 호출
- Application deadlineAt 변경 시 queue update 호출
- Application 삭제 시 queue remove 호출 후 삭제
- ScheduleEvent 생성 시 queue enqueue 호출
- ScheduleEvent startAt 변경 시 queue update 호출
- ScheduleEvent 삭제 시 queue remove 호출 후 삭제
- `ScheduleSyncService`의 자동 JOB_POSTING sync 4분기에서 enqueue/update 미호출, 삭제 분기 remove 호출 검증

### 9.3 통합 테스트

- Redis ZSET에 score/value가 기대 형태로 저장됨
- scheduler 실행 후 `notifications` row 생성 및 unread count 증가
- `GET /api/notifications?unreadOnly=true`가 읽지 않은 알림만 반환
- `PATCH /api/notifications/{id}/read` 후 unread count 감소
- `PATCH /api/notifications/read-all` 후 unread count 0
- Application/Schedule 원본 삭제 후 남은 Redis 예약 item이 제거됨

---

## 10. 구현 순서 제안

1. Redis 의존성(`spring-boot-starter-data-redis`)과 local Redis 설정 추가
2. `Notification` 엔티티, enum, repository, migration 작성
3. `NotificationQueue` port와 Redis 구현 작성
4. trigger 계산 유틸과 queue 단위 테스트 작성
5. `NotificationCommandService`, `NotificationQueryService` 작성
6. `NotificationDueScheduler` 작성
7. 알림 API 인터페이스·컨트롤러 작성
8. Application/Schedule 서비스에 queue 호출 wire-up
9. 통합 테스트로 Redis ZSET, scheduler, API 읽음 흐름 검증

---

## 11. 남은 결정

| 항목 | v1 결정 | 재검토 시점 |
|---|---|---|
| 실시간 전달 | polling/API 조회 | 프론트에서 즉시성 요구가 생기면 SSE 우선 검토 |
| 외부 push | 미지원 | 모바일 앱 또는 PWA 요구가 생기면 FCM/브라우저 push 검토 |
| 알림 시간 | D-3/D-1/당일 고정 | 사용자 설정 화면 도입 시 |
| 보관/삭제 | 자동 삭제 없음 | 운영 데이터 증가 후 보관 기간 정책 결정 |
| cursor | id 기반 | 대량 데이터에서 createdAt/id keyset으로 확장 |
