# notification 도메인 — 동작 흐름과 설계 선택

이 문서는 `notification/tech.md`를 읽기 전에 알림 기능이 실제로 어떻게 움직이는지 이해하기 위한 설명 문서다. 구현 상세보다 "왜 Redis Sorted Set을 쓰는지", "알림이 언제 등록되고 언제 DB에 생기는지", "사용자가 어떻게 조회·읽음·삭제 흐름을 타는지"에 초점을 둔다.

---

## 1. 한 줄 요약

웹 내부 알림은 두 단계로 나뉜다.

1. Redis Sorted Set에 "미래에 알림을 만들어야 하는 예약"을 넣는다.
2. 예약 시간이 되면 scheduler가 Redis에서 꺼내 DB `notifications` row를 만들고, 사용자는 DB 알림함을 조회한다.

Redis는 알림함이 아니다. Redis는 "언제 DB 알림을 만들지"를 기억하는 타이머 큐다. 사용자가 보는 실제 알림 목록, 읽음 상태, 생성 시각은 DB가 담당한다.

```text
Application/Schedule 변경
  → Redis에 예약 등록
  → 시간이 됨
  → Scheduler가 DB notifications row 생성
  → 프론트가 GET /api/notifications로 조회
  → 사용자가 읽음 처리
```

---

## 2. 왜 Redis Sorted Set을 쓰는가

알림 시스템에서 핵심 질문은 이것이다.

> "지금 시각 기준으로 발동해야 하는 예약 알림이 무엇인가?"

Redis Sorted Set은 각 item에 score를 붙여 정렬된 집합을 만든다. 여기서는 `triggerAt`을 score로 쓴다.

```text
key   = notifications:due
score = triggerAt epoch millis
value = APPLICATION:101:D_MINUS_1:99
```

그러면 scheduler는 매분 다음처럼 물어볼 수 있다.

```text
triggerAt <= now 인 item 100개만 줘
```

Redis 명령으로는 대략 `ZRANGEBYSCORE notifications:due -inf now LIMIT 0 100` 형태다.

### DB만 쓰지 않는 이유

DB 테이블에 예약 알림을 모두 넣고 `WHERE trigger_at <= now AND status = 'PENDING'`으로 조회하는 방법도 가능하다. 하지만 v1에서 Redis를 쓰면 다음 장점이 있다.

- 매분 DB pending table을 스캔하지 않는다.
- 시간순 due 조회가 Redis ZSET에 잘 맞는다.
- `Application.deadlineAt`이나 `ScheduleEvent.startAt` 변경 시 기존 예약 제거 후 재등록이 단순하다.
- 아직 사용자에게 보여줄 알림이 아닌 "예약"과 실제 알림함 row를 분리할 수 있다.

핵심은 Redis가 빠르기 때문만이 아니다. "미래 예약 큐"와 "사용자 알림함"을 분리하면 모델이 명확해진다.

---

## 3. 주요 시간 개념

비슷한 시간이 여러 개 나오므로 구분이 중요하다.

| 이름 | 의미 | 예시 |
|---|---|---|
| `scheduledAt` | 원본 일정/마감 시각 | 채용 마감 `2026-05-10T14:00:00Z` |
| `triggerAt` | 알림을 만들어야 하는 시각 | D-1이면 `2026-05-09T14:00:00Z` |
| `createdAt` | DB 알림 row가 실제 생성된 시각 | scheduler가 처리한 시각 |
| `readAt` | 사용자가 알림을 읽음 처리한 시각 | 읽지 않았으면 null |

사용자가 조회하는 알림에는 `scheduledAt`, `createdAt`, `readAt`이 들어간다. `triggerAt`은 Redis 예약 큐 내부 계산값이라 API 응답에는 보통 노출하지 않는다.

---

## 4. 알림 등록 과정

### 4.1 Application 마감 알림

사용자가 지원 카드를 만들거나 마감일을 바꾸면 Application Service가 동작한다.

```text
POST /api/applications
  ├─ Application 저장
  ├─ deadlineAt 확인
  └─ notificationQueue.enqueueApplicationDeadline(userId, applicationId, deadlineAt)
```

마감일이 `2026-05-10T14:00:00Z`라면 3개의 예약 후보가 생긴다.

```text
D_MINUS_3 → 2026-05-07T14:00:00Z
D_MINUS_1 → 2026-05-09T14:00:00Z
D_DAY     → 2026-05-10T14:00:00Z
```

현재 시각보다 미래인 후보만 Redis에 들어간다.

```text
ZADD notifications:due 1778162400000 APPLICATION:101:D_MINUS_3:99
ZADD notifications:due 1778335200000 APPLICATION:101:D_MINUS_1:99
ZADD notifications:due 1778421600000 APPLICATION:101:D_DAY:99
```

마감일을 변경하면 기존 `APPLICATION:101:*:99` 예약을 제거하고 새 마감일 기준으로 다시 넣는다.

```text
remove old APPLICATION:101:*:99
add new APPLICATION:101:D_MINUS_3:99
add new APPLICATION:101:D_MINUS_1:99
add new APPLICATION:101:D_DAY:99
```

### 4.2 ScheduleEvent 알림

사용자가 직접 등록한 일정도 같은 방식이다. 차이는 source가 `SCHEDULE_EVENT`이고 기준 시각이 `ScheduleEvent.startAt`이라는 점뿐이다.

Application deadline에서 자동 동기화되는 `JOB_POSTING` 일정은 달력 표시용 mirror이므로 `SCHEDULE_EVENT` 알림을 별도로 만들지 않는다. 같은 마감 알림은 `APPLICATION` source가 담당한다.

```text
POST /api/schedule/events
  ├─ ScheduleEvent 저장
  └─ notificationQueue.enqueueScheduleEvent(userId, eventId, startAt)
```

일정 시간이 바뀌면 기존 `SCHEDULE_EVENT:{eventId}:*:{userId}` 예약을 제거한 뒤 새 startAt 기준으로 재등록한다.

### 4.3 과거 trigger는 등록하지 않는다

예를 들어 지금이 2026-05-09이고 마감일이 2026-05-10이면 D-3은 이미 지났다. 이 경우 D-3 예약은 만들지 않고 D-1, D-DAY만 등록한다.

과거 알림을 뒤늦게 만들어 사용자를 혼란스럽게 하지 않기 위한 정책이다.

---

## 5. 예약 처리 과정

매분 scheduler가 Redis를 확인한다.

```text
NotificationDueScheduler
  ├─ now = 현재 UTC 시각
  ├─ Redis에서 triggerAt <= now item 조회
  ├─ value 파싱
  ├─ source 조회
  ├─ DB notifications row 생성
  └─ 성공한 item은 Redis에서 제거
```

예를 들어 Redis에 이런 item이 due 상태가 됐다고 하자.

```text
APPLICATION:101:D_MINUS_1:99
```

scheduler는 다음 순서로 처리한다.

1. `ApplicationRepository.findByIdAndUserId(101, 99)`로 원본 카드가 아직 있는지 확인한다.
2. 회사명과 deadlineAt으로 알림 문구를 만든다.
3. DB에 `notifications` row를 insert한다.
4. insert 성공 후 Redis에서 해당 item을 `ZREM`한다.

생성되는 DB row는 이런 형태다.

```text
userId      = 99
sourceType  = APPLICATION
sourceId    = 101
remindType  = D_MINUS_1
title       = "토스 채용 마감 D-1"
message     = "토스 채용 마감이 1일 남았습니다."
scheduledAt = 2026-05-10T14:00:00Z
readAt      = null
```

원본 Application이나 ScheduleEvent가 이미 삭제됐다면 DB 알림을 만들지 않고 Redis item만 제거한다.

---

## 6. 알림 조회 과정

사용자가 웹 사이트에 들어오면 프론트가 DB 알림 API를 호출한다.

```text
GET /api/notifications?unreadOnly=false&limit=20
```

백엔드는 Redis를 조회하지 않는다. 사용자가 보는 알림은 이미 DB `notifications`에 만들어진 것만이다.

```text
NotificationQueryService
  ├─ userId 기준으로 notifications 조회
  ├─ unreadOnly=true면 readAt IS NULL 조건 추가
  ├─ 최신순 정렬
  └─ NotificationResponse 반환
```

읽지 않은 알림 수는 다음 API로 조회한다.

```text
GET /api/notifications/unread-count
```

이 API도 DB에서 `user_id = ? AND read_at IS NULL` 조건으로 count한다.

---

## 7. 읽음 처리 과정

단건 읽음 처리는 알림 row의 `readAt`을 채우는 작업이다.

```text
PATCH /api/notifications/{id}/read
  ├─ findByIdAndUserId(id, userId)
  ├─ 없으면 NOTIFICATION_NOT_FOUND
  ├─ readAt == null 이면 현재 시각 저장
  └─ 이미 읽었으면 그대로 반환
```

전체 읽음 처리는 해당 사용자의 unread row들을 한 번에 갱신한다.

```text
PATCH /api/notifications/read-all
  └─ UPDATE notifications
     SET read_at = now
     WHERE user_id = ? AND read_at IS NULL
```

읽음 처리는 Redis와 무관하다. Redis는 아직 DB 알림이 되지 않은 예약만 관리한다.

---

## 8. 삭제와 정리 과정

삭제는 두 종류를 구분해야 한다.

### 8.1 원본 리소스 삭제

Application이나 ScheduleEvent가 삭제되면 아직 발동하지 않은 예약 알림은 제거해야 한다.

```text
ApplicationService.delete
  ├─ notificationQueue.removeByApplicationId(userId, applicationId)
  └─ applicationRepository.delete(application)
```

```text
ScheduleEventCommandService.delete
  ├─ notificationQueue.removeByEventId(userId, eventId)
  └─ scheduleEventRepository.delete(event)
```

여기서 제거하는 것은 Redis 예약 item이다.

이미 DB에 생성된 알림 row는 v1에서 자동 삭제하지 않는다. 사용자가 이미 받은 알림 기록이기 때문이다. 다만 product 정책상 "원본 삭제 시 기존 알림도 지운다"로 바꾸고 싶다면 `notifications`에서 `sourceType/sourceId` 기준 삭제를 추가하면 된다.

### 8.2 알림 자체 삭제

v1 API에는 알림 삭제를 넣지 않는다. 목록 조회와 읽음 처리만 제공한다.

삭제 API가 필요해지면 다음 중 하나를 선택해야 한다.

| 방식 | 의미 |
|---|---|
| hard delete | 사용자가 알림 row를 실제 삭제 |
| soft hide | `hidden_at` 같은 컬럼으로 사용자 화면에서만 숨김 |

v1에서는 단순성을 위해 삭제 API를 미룬다.

---

## 9. 실패 처리

### 9.1 Redis 등록 실패

Application/Schedule 변경 중 Redis 등록이 실패하면 전체 요청을 실패시킨다.

이유는 DB에는 마감일이 바뀌었는데 Redis 예약 큐는 예전 상태로 남는 상황을 피하기 위해서다. v1은 가용성보다 정합성을 우선한다.

### 9.2 scheduler DB insert 실패

DB 알림 생성에 실패하면 Redis item을 제거하지 않는다. 다음 scheduler tick에서 재시도한다.

### 9.3 DB insert 성공 후 Redis 제거 실패

다음 tick에서 같은 item이 다시 처리될 수 있다. 이때 DB unique 제약이 중복 알림 생성을 막는다.

```text
UNIQUE (user_id, source_type, source_id, remind_type, scheduled_at)
```

같은 기준 시각으로 이미 생성된 알림이면 중복 insert를 무시하고 Redis item만 다시 제거한다. deadline/startAt이 바뀌어 `scheduled_at`이 달라진 경우에는 같은 source/remindType이어도 새 알림을 다시 생성한다.

---

## 10. 트레이드오프

### 장점

- 매분 DB 전체를 스캔하지 않아도 된다.
- due item만 빠르게 꺼낼 수 있다.
- 예약 큐와 사용자 알림함이 분리되어 역할이 명확하다.
- 알림 읽음 상태는 DB에 있으므로 영속성과 조회가 안정적이다.
- Redis item 중복 처리에도 DB unique 제약으로 최종 중복 알림을 막을 수 있다.

### 단점

- Redis와 DB를 함께 다루므로 구현 복잡도가 올라간다.
- Redis 장애 시 Application/Schedule 변경 요청도 실패할 수 있다.
- DB transaction과 Redis operation은 원자적으로 묶이지 않는다.
- scheduler, Redis queue parser, 중복 처리 같은 운영 코드가 필요하다.
- Redis 데이터가 유실되면 아직 DB에 생성되지 않은 예약 알림을 잃을 수 있다.

### 이 설계가 감수하는 점

v1은 "알림이 틀리게 남는 것"보다 "Redis 장애 시 관련 요청이 실패하는 것"을 선택한다. 채용 마감 알림은 정합성이 중요하고, Redis 호출은 일반적으로 짧기 때문에 이 정책을 우선한다.

---

## 11. 다른 대안

### 대안 1. DB pending table만 사용

예약 알림도 DB에 저장한다.

```text
notification_jobs(id, user_id, source_type, source_id, remind_type, trigger_at, status)
```

scheduler는 매분 DB에서 `trigger_at <= now AND status = 'PENDING'`을 조회한다.

| 장점 | 단점 |
|---|---|
| DB만 쓰므로 구조가 단순하다 | 매분 pending job 조회 부하가 DB로 간다 |
| transaction 처리가 쉽다 | 동시 scheduler 처리 시 lock/claim 정책이 필요하다 |
| Redis 유실 걱정이 없다 | job table과 notification table을 둘 다 관리해야 한다 |

초기 트래픽이 작고 Redis 도입을 늦추고 싶다면 이 방식도 충분히 현실적이다.

### 대안 2. 알림을 미리 DB에 생성

Application/Schedule 생성 시 D-3/D-1/D-DAY 알림 row를 바로 DB에 만든다. 단, `visible_at` 전에는 조회하지 않는다.

```text
notifications(..., visible_at, read_at)
```

조회 시 `visible_at <= now` 조건을 붙인다.

| 장점 | 단점 |
|---|---|
| scheduler가 없어도 된다 | 아직 사용자에게 보이지 않을 알림 row가 미리 생긴다 |
| 구현이 가장 단순하다 | 마감일 변경 시 기존 row 수정/삭제 정책이 까다롭다 |
| Redis가 필요 없다 | 알림 row가 "예약"과 "받은 알림" 두 의미를 동시에 가진다 |

v1을 아주 단순하게 시작하려면 좋은 선택이다. 다만 알림함 row의 의미가 흐려지고, 일정 변경이 많을 때 row 관리가 지저분해질 수 있다.

### 대안 3. Redis Stream 또는 메시지 큐 사용

Kafka, RabbitMQ, Redis Stream 같은 메시지 큐를 사용한다.

| 장점 | 단점 |
|---|---|
| 처리량과 재처리 모델이 강하다 | 현재 규모에는 과하다 |
| consumer group 등 운영 기능이 있다 | 지연 실행 예약은 별도 구현이 필요하다 |
| 장애 처리 패턴이 명확하다 | 인프라 복잡도가 크다 |

대량 알림, 여러 consumer, 외부 push 채널이 생긴 뒤 검토하는 편이 낫다.

### 대안 4. Quartz/Spring Scheduler DB job

Quartz 같은 scheduler 라이브러리로 job을 예약한다.

| 장점 | 단점 |
|---|---|
| 예약 실행 모델이 이미 있다 | 도메인 단순 알림에 비해 무겁다 |
| misfire 정책 등 기능이 많다 | job store 운영과 설정 비용이 있다 |
| 반복/복잡한 스케줄에 강하다 | D-3/D-1/D-DAY 단순 알림에는 과설계일 수 있다 |

반복 일정, 복잡한 캘린더 규칙, 관리자 재시도 UI가 필요해질 때 검토한다.

---

## 12. 현재 권장안

현재 프로젝트의 v1에는 다음 방식이 가장 균형이 좋다.

```text
Redis Sorted Set = 미래 예약 큐
DB notifications = 사용자 알림함
Scheduler = due 예약을 DB 알림으로 변환하는 작업자
```

이 방식은 DB 알림함의 영속성과 Redis 예약 큐의 due 조회 성능을 나눠 가진다. 동시에 이메일/FCM 같은 외부 발송을 넣지 않는 v1 범위에도 잘 맞는다.

단, 구현을 더 단순하게 시작하고 싶다면 "대안 2. 알림을 미리 DB에 생성하고 visible_at으로 조회"도 현실적인 MVP 선택지다. Redis가 아직 프로젝트 인프라에 들어오지 않았다면 이 대안을 먼저 구현하고, 알림 규모가 커질 때 Redis 예약 큐로 바꾸는 순서도 가능하다.
