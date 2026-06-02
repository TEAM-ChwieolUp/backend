# notification TODO

알림 기능 구현 후 요구사항 대비 아직 남은 작업을 정리한다. `tech.md`와 `flow.md`의 요구사항을 기준으로 한다.

---

## 1. 중복 방지 정책 보완 — 완료

### 처리 결과

- 정책은 "기록 보존"으로 확정했다.
- unique key를 `(user_id, source_type, source_id, remind_type, scheduled_at)` 기준으로 변경했다.
- `NotificationDueService`는 source의 현재 `deadlineAt`/`startAt`을 먼저 읽은 뒤, `scheduledAt` 포함 조건으로 중복 여부를 확인한다.
- 같은 source/remindType이라도 `scheduledAt`이 다르면 새 알림을 다시 생성한다.
- `NotificationDueServiceTest`에 새 기준 시각 생성 및 같은 기준 시각 중복 skip 케이스를 추가했다.

---

## 2. ScheduleSyncService 알림 큐 연동 보완 — 완료

### 결정

v1은 `Application.deadlineAt` 알림을 `APPLICATION` source만 사용한다.

`ScheduleSyncService`가 Application deadline으로 자동 생성/갱신하는 `JOB_POSTING` 이벤트는 달력 표시용 mirror이므로 `notificationQueue.enqueueScheduleEvent` 또는 `updateScheduleEvent`를 호출하지 않는다.

삭제 분기에서는 과거 버전이나 정책 변경으로 남아 있을 수 있는 stale queue 제거를 위해 `removeByEventId`만 호출한다.

### 처리 내용

- `notification/tech.md`, `notification/flow.md`, `schedule/tech.md`에서 자동 `JOB_POSTING` enqueue/update 요구를 제거했다.
- `ScheduleSyncService` 4분기 테스트를 추가해 자동 `JOB_POSTING` 생성/갱신 시 schedule notification enqueue/update가 호출되지 않음을 검증한다.
- 자동 `JOB_POSTING` 삭제와 Application 삭제 시 stale schedule notification queue 제거는 계속 검증한다.

---

## 3. 알림 목록 정렬과 cursor 정책 정합화 — 완료

### 결정

v1은 단순 id cursor를 공식 정책으로 삼는다.

- 정렬 기준: `id DESC`
- cursor 의미: 이전 페이지 마지막 notification id
- 다음 페이지 조건: `id < cursor`
- `createdAt`은 응답 표시용으로만 사용하고 정렬 기준에서는 제외한다.

### 처리 내용

- `NotificationRepository.findSlice` query 파라미터를 `cursorId`로 명확히 했다.
- `tech.md`의 정렬/인덱스/응답 설명을 id cursor 기준으로 맞췄다.
- `NotificationQueryServiceTest`를 추가해 id 정렬, nextCursor, unread cursor, limit 검증을 고정했다.

---

## 4. 테스트 보강

### 현재 상태

`compileKotlin`, `compileTestKotlin`은 통과했다. `./gradlew test`는 Gradle test worker 실행 환경 문제로 완료 검증하지 못했다.

확인된 환경 오류:

```text
Could not find or load main class worker.org.gradle.process.internal.worker.GradleWorkerMain
ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain
```

### TODO

- Gradle test worker 환경 문제를 해결한다.
  - 실행 중인 Gradle/Java 프로세스 정리
  - `.gradle-user-home` 캐시 재생성
  - 필요 시 OneDrive 밖 짧은 ASCII 경로에서 재실행
- 아래 테스트를 추가/보강한다.
  - Redis ZSET 실제 enqueue/update/remove 검증
  - `ApplicationService` deadline 생성/변경/삭제 시 queue 호출 검증
  - `ScheduleEventCommandService` 생성/startAt 변경/삭제 시 queue 호출 검증
  - `ScheduleSyncService` 정책 확정 후 queue 호출 검증
  - scheduler가 due item을 DB 알림으로 만들고 Redis에서 제거하는 통합 테스트
  - deadline/startAt 변경 후 같은 remindType 알림 재생성 회귀 테스트

---

## 5. 문서 동기화

### TODO

- `notification/tech.md`와 `notification/flow.md`를 최종 정책에 맞게 갱신한다.
- `application/tech.md`, `schedule/tech.md`에 남아 있는 notification TODO 또는 오래된 설명을 현재 구현 기준으로 정리한다.
- 루트 `AGENTS.md`/`CLAUDE.md`의 notification 설명이 오래된 Redis 단독 구조로 남아 있다면 "Redis 예약 큐 + DB 알림함"으로 갱신한다.
