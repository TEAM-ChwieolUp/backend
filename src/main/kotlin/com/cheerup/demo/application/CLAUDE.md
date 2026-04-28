# application — 채용 칸반 보드

지원 카드와 보드 구조를 관리하는 핵심 도메인. 4개 엔티티가 한 패키지에 묶임 (응집도 우선).

## 도메인 구조

```
Stage (칸반 컬럼) ←──── stageId ──── Application (지원 카드)
                                          │
                                          ↓ M:N
                                   ApplicationTag ──── Tag
```

## 엔티티

### Stage — 칸반 보드 컬럼

사용자별 커스터마이즈 가능한 데이터. **enum이 아닌 엔티티**.

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK |
| `userId` | `Long` (val) | NOT NULL, INDEX, FK ON DELETE CASCADE |
| `name` | `String` | NOT NULL — "서류전형", "1차 면접" 등 자유 |
| `displayOrder` | `Int` | NOT NULL — 칸반 좌→우 순서 |
| `color` | `String` | NOT NULL — hex |
| `category` | `StageCategory` | NOT NULL |

```kotlin
enum class StageCategory {
    IN_PROGRESS,  // 진행 중 (기본)
    PASSED,       // 최종 합격
    REJECTED,     // 탈락 / 포기
}
```

`category`는 시스템 의미. 마감 알림은 `IN_PROGRESS` 카드에만 발송, 합격률 통계는 `PASSED` 비율 등에 사용.

### Application — 지원 카드

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK |
| `userId` | `Long` (val) | NOT NULL, INDEX, FK ON DELETE CASCADE |
| `stageId` | `Long` (var) | NOT NULL, INDEX (FK 안 검 — Stage 삭제는 Service에서 카드 이동 처리) |
| `companyName` | `String` | NOT NULL |
| `position` | `String` | NOT NULL |
| `appliedAt` | `Instant?` | |
| `deadlineAt` | `Instant?` | INDEX — 마감 알림 폴링용 |
| `noResponseDays` | `Int?` | 무응답 기준 일수, 기본 7 |
| `priority` | `Priority` | NOT NULL, default `NORMAL` |
| `memo` | `String?` (TEXT) | |
| `jobPostingUrl` | `String?` | |

```kotlin
enum class Priority { LOW, NORMAL, HIGH }
```

도메인 메서드:
- `changeStage(newStageId: Long)` — Service에서 새 stage가 같은 userId 소유인지 검증 후 호출
- `updateMemo(memo: String?)`
- `setDeadline(deadlineAt: Instant?)` — 알림 큐 갱신은 Service 책임

### Tag

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK |
| `userId` | `Long` (val) | NOT NULL, FK ON DELETE CASCADE, **`(user_id, name)` UNIQUE** |
| `name` | `String` | NOT NULL |
| `color` | `String` | NOT NULL |

### ApplicationTag — M:N 조인

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK |
| `applicationId` | `Long` | NOT NULL, **FK → applications(id) ON DELETE CASCADE** |
| `tagId` | `Long` | NOT NULL, **FK → tags(id) ON DELETE CASCADE** |
| | | **`(application_id, tag_id)` UNIQUE** |

## 마감일 정책

`Application.deadlineAt`이 마감일 **원본**. `ScheduleEvent`에 별도 row를 만들지 않음.
달력 API는 사용자 카드들의 `deadlineAt`을 읽어 `JOB_POSTING` 카테고리 이벤트로 **변환만** 해서 응답.
중복 저장 시 동기화 실패 위험을 피하기 위함.

## 삭제 정책

| 부모 | 정책 | 자식 처리 |
|---|---|---|
| `User` 삭제 | DB FK CASCADE | 모든 도메인 엔티티 자동 |
| `Application` 삭제 | DB FK CASCADE + Service 명시 혼합 | `ApplicationTag`/`Retrospective`는 자동, `ScheduleEvent`는 Service에서 (외부 시스템 정리 필요) |
| `Stage` 삭제 | Service 검증 — **비어있을 때만 삭제** | 해당 단계에 `Application`이 1개라도 있으면 `STAGE_NOT_EMPTY` 예외 (409 Conflict) |
| `Tag` 삭제 | DB FK CASCADE | `ApplicationTag` 자동 |

```kotlin
@Transactional
fun deleteApplication(userId: Long, id: Long) {
    val app = applicationRepository.findByIdAndUserId(id, userId)
        ?: throw BusinessException(ErrorCode.APPLICATION_NOT_FOUND)

    // 외부 시스템 정리
    val events = scheduleEventRepository.findByApplicationId(id)
    events.forEach { calendarExporter.cancelExternalExport(it) }
    scheduleEventRepository.deleteAll(events)
    notificationQueue.removeByApplicationId(id)

    // ApplicationTag, Retrospective는 DB CASCADE로 자동
    applicationRepository.delete(app)
}

@Transactional
fun deleteStage(userId: Long, stageId: Long) {
    val stage = stageRepository.findByIdAndUserId(stageId, userId)
        ?: throw BusinessException(ErrorCode.STAGE_NOT_FOUND)

    val cardCount = applicationRepository.countByUserIdAndStageId(userId, stageId)
    if (cardCount > 0) {
        throw BusinessException(ErrorCode.STAGE_NOT_EMPTY)   // 카드 있으면 삭제 불가
    }
    stageRepository.delete(stage)
}
```

## Stage 삭제 정책 — "비어있어야 삭제 가능"

`Stage`를 삭제하려면 그 단계에 속한 `Application` 카드가 **0개**여야 한다.

```
사용자가 Stage 삭제 요청
  ├─ 카드 0개  → 삭제 성공
  └─ 카드 ≥1개 → 409 STAGE_NOT_EMPTY 예외
                  → 사용자에게 "카드 N개를 먼저 다른 단계로 옮기거나 삭제해주세요"
```

### 왜 자동 이동이 아닌 명시적 차단인가

| 옵션 | 평가 |
|---|---|
| ❌ 자동으로 다른 stage로 이동 | 사용자가 의도하지 않은 곳으로 카드가 흘러감 — "탈락" 카드가 "진행 중"으로 섞이는 사고 |
| ❌ DB FK CASCADE로 카드도 삭제 | 사용자 데이터 유실 위험 — 회복 불가 |
| ✅ **카드 비어있을 때만 삭제 허용** | 사용자가 명시적으로 카드를 정리한 후 삭제 — 사고 없음 |

### UX 흐름

1. 프론트는 보드에서 각 Stage의 카드 개수를 알고 있음 → 카드 ≥1개면 삭제 버튼 비활성화 또는 경고 표시 (사전 차단)
2. 그래도 요청이 들어오면 백엔드가 한 번 더 검증 → `STAGE_NOT_EMPTY` 응답
3. 사용자는 (a) 카드를 다른 단계로 드래그 또는 (b) 카드 자체를 삭제한 뒤 Stage 삭제 재시도

## Stage 시드

신규 가입 시 `StageSeedService.seedDefault(userId)` 호출 — `user/` 도메인의 회원가입 흐름에서 트리거.

| order | name | category |
|---|---|---|
| 0 | 관심 기업 | IN_PROGRESS |
| 1 | 서류 전형 | IN_PROGRESS |
| 2 | 면접 | IN_PROGRESS |
| 3 | 최종 합격 | PASSED |
| 4 | 불합격 | REJECTED |

## 단계 전이 검증

`changeStage` 호출 시 Service에서 다음을 검증:

1. 새 `stageId`가 같은 `userId` 소유의 Stage인가 (IDOR 방지)
2. 그 외 비즈니스 규칙은 두지 않음 (사용자가 자유롭게 보드를 운영)

## API 요약

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/applications` | 칸반 보드 조회 (`stage`, `tag` 필터) |
| POST | `/api/applications` | 카드 등록 |
| PATCH | `/api/applications/{id}` | 카드 수정 (단계/메모/태그 변경) |
| DELETE | `/api/applications/{id}` | 카드 삭제 |
| GET | `/api/stages` | Stage 목록 |
| POST | `/api/stages` | Stage 추가 (`category` 필수) |
| PATCH | `/api/stages/{id}` | Stage 이름/색상/순서/카테고리 수정 |
| DELETE | `/api/stages/{id}` | Stage 삭제 (해당 단계 카드 0개일 때만 가능) |
| GET/POST/PATCH/DELETE | `/api/tags` | Tag CRUD |

## 권장 인덱스

- `applications(user_id, stage_id)` — 칸반 보드 조회
- `applications(user_id, deadline_at)` — 마감 알림 폴링
- `application_tags(tag_id)` — 태그별 필터
- `stages(user_id, display_order)` — 보드 컬럼 조회
