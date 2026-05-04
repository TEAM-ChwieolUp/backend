# application 도메인 — 기술 스펙 (tech.md)

이 문서는 `application/` 도메인(채용 칸반 보드)의 **구현 진입 직전 설계 명세**다. 도메인 모델·정책의 *왜*는 [`CLAUDE.md`](./CLAUDE.md)에 있다. 본 문서는 그것을 받아 *어떻게 동작하는지* — API 계약, 플로우, 검증, 트랜잭션, 테스트 — 를 정의한다.

> 깊이: **설계 수준**. 필드 시그니처·메서드 본문은 구현 단계에서 결정하되, 본 문서가 정한 입출력 형태·플로우·에러 코드는 그대로 따른다.

---

## 1. Scope

### In Scope (이 도메인이 책임짐)
- `Stage`, `Application`, `Tag`, `ApplicationTag` 4개 엔티티의 영속화·조회·변경
- 칸반 보드 조회 / 카드 CRUD / 단계 이동 / 태그 부착·해제
- Stage CRUD (시드 단계 보호 포함)
- Tag CRUD
- Application의 `deadlineAt`이 변경될 때 알림 큐 갱신을 `notification/`에 위임
- Application 삭제 시 외부 시스템(스케줄·알림) 정리 호출

### Out of Scope (다른 도메인이 처리)
| 책임 | 위임 대상 |
|---|---|
| 마감 알림 발송·스케줄링 | `notification/` (Redis Sorted Set) |
| 회고 작성·조회 | `retrospective/` (`Application` 삭제 시 DB CASCADE로만 영향) |
| 일정 표시·내보내기 | `schedule/` (`deadlineAt` 변경 시 `JOB_POSTING` `ScheduleEvent` row를 동기화 저장, iCalendar export) |
| AI 메일 분류 → 단계 변경 제안 | `ai/`, `mail/` (`Suggestion` 형태로 보내고, 본 도메인은 수락 후 `changeStage`만 호출됨) |
| 사용자 컨텍스트 / JWT | `global/jwt`, `@CurrentUser` |

### 비기능 요구
- 칸반 CRUD API: **p95 ≤ 200ms**
- 모든 엔드포인트 IDOR 방지 — 요청 `userId`와 리소스 소유자 일치 검증 필수
- Soft delete 통일: `BaseEntity.deletedAt` (현재 미적용 도메인은 hard delete 허용. **본 도메인은 Application/Stage/Tag 모두 hard delete + DB CASCADE.** 회고/통계 보존이 사용 사례에 없음)

---

## 2. Data Model 개요

세부 필드 정의는 `CLAUDE.md` §엔티티 참조. 이 절은 **저장소 계층의 의사결정**만 다룬다.

### 패키지 배치

`Stage`, `Application`, `Tag`, `ApplicationTag` 4개 엔티티 클래스와 관련 enum(`StageCategory`, `Priority`)은 **모두 `application/domain/` 아래에 둔다.** Repository/Service/Controller/DTO는 형제 패키지로 분리 — JPA 엔티티가 `dto/`나 `service/`로 새지 않게 한다.

```
application/
├── controller/    # ApplicationController, StageController, TagController
├── service/       # ApplicationService, StageService, TagService, StageSeedService
├── repository/    # ApplicationRepository, StageRepository, TagRepository, ApplicationTagRepository
├── domain/        # Application, Stage, Tag, ApplicationTag + StageCategory, Priority
└── dto/           # *Request, *Response data class
```

### 테이블/제약 요약

| 테이블 | PK | 유니크 | 외래키 (DB 레벨) |
|---|---|---|---|
| `stages` | `id` | — | `user_id → users(id) ON DELETE CASCADE` |
| `applications` | `id` | — | `user_id → users(id) ON DELETE CASCADE`<br>`stage_id`는 **FK 미설정** (Service에서 무결성 책임) |
| `tags` | `id` | `(user_id, name)` | `user_id → users(id) ON DELETE CASCADE` |
| `application_tags` | `id` | `(application_id, tag_id)` | `application_id → applications(id) ON DELETE CASCADE`<br>`tag_id → tags(id) ON DELETE CASCADE` |

**`applications.stage_id`에 FK를 걸지 않는 이유**: Stage 삭제 정책이 Service에서 "비어있을 때만 삭제 허용"이므로 DB FK가 추가 보호 없이 마이그레이션 비용만 만든다. 단계 이동 시 무결성은 Service의 `findStageByIdAndUserId` 검증으로 확보.

### 인덱스

| 인덱스 | 사용처 |
|---|---|
| `applications(user_id, stage_id)` | 칸반 보드 조회의 1차 필터 |
| `applications(user_id, deadline_at)` | `notification/`의 마감 폴링 (NULLS LAST 정렬 — MySQL은 NULL이 작게 정렬되므로 쿼리에서 `deadline_at IS NOT NULL` 명시) |
| `application_tags(tag_id)` | 태그별 카드 검색 (M:N 역방향) |
| `stages(user_id, display_order)` | 보드 컬럼 순서 |
| `tags(user_id, name)` UNIQUE | 같은 사용자 내 태그 이름 중복 방지 |

### Soft delete

- 본 도메인은 모두 hard delete. `BaseEntity.deletedAt`은 두되 **현재 미사용**. 향후 회고 보존 요구가 생기면 `Application`에만 적용을 검토.

---

## 3. API 계약

공통 규약:
- **인증**: 모든 엔드포인트 `Authorization: Bearer <JWT>` 필수
- **응답**: 성공 시 `{ "data": ..., "meta": { timestamp, requestId } }`
- **오류**: RFC 7807 Problem Details + `ErrorCode` 코드 (예: `{"type":"about:blank","title":"...","status":404,"code":"APPLICATION_NOT_FOUND",...}`)
- **시간**: 모든 시각은 ISO-8601 UTC (`2026-05-01T03:00:00Z`)
- **페이지네이션**: 칸반은 사용자당 카드 수가 적어 (~수백 건) 페이지네이션 없음. Stage/Tag도 동일

### 3.1 칸반 보드

#### `GET /api/applications`
칸반 보드 전체 조회. Stage 그룹별로 묶어서 반환.

**Query**
| 이름 | 타입 | 설명 |
|---|---|---|
| `stage` | `Long?` | 특정 Stage만 |
| `tag` | `Long?` | 특정 Tag가 부착된 카드만 (다중 태그는 v1에서 미지원) |
| `priority` | `LOW\|NORMAL\|HIGH?` | |

**응답 200**
```json
{
  "data": {
    "stages": [
      {
        "id": 1, "name": "관심 기업", "displayOrder": 0, "color": "#888",
        "category": "IN_PROGRESS",
        "applications": [
          {
            "id": 101, "companyName": "토스", "position": "Backend",
            "appliedAt": "2026-04-20T00:00:00Z", "deadlineAt": "2026-05-10T14:00:00Z",
            "priority": "HIGH", "memo": "...", "jobPostingUrl": "https://...",
            "tags": [{"id": 5, "name": "원격", "color": "#0a0"}]
          }
        ]
      }
    ]
  },
  "meta": { ... }
}
```

**구현 노트**
- 단일 쿼리로 `applications + application_tags + tags`를 fetch join. Stage는 별도 쿼리로 가져와 인메모리에서 그룹화.
- 빈 Stage도 응답에 포함 (드래그앤드롭 타깃이 되어야 함).

---

#### `POST /api/applications`
카드 등록.

**요청**
```json
{
  "companyName": "토스", "position": "Backend",
  "stageId": 2,
  "appliedAt": "2026-04-20T00:00:00Z",
  "deadlineAt": "2026-05-10T14:00:00Z",
  "priority": "HIGH",
  "memo": null, "jobPostingUrl": "https://...",
  "tagIds": [5, 7]
}
```

**검증**
- `companyName`, `position`: `@NotBlank`, 1..100자
- `stageId`: 필수, **요청자 소유의 Stage**여야 함 (`STAGE_NOT_FOUND` 404)
- `deadlineAt`: 미래일 필요 없음 (지난 마감의 회고 등록 케이스 허용)
- `tagIds`: 모두 요청자 소유 태그 (`TAG_NOT_FOUND` 404). 부분 실패 시 전체 실패 (트랜잭션)
- `noResponseDays`: 1..365 (기본 7)

**응답 201**: 등록된 단일 `ApplicationResponse`.

**부수 효과**: `deadlineAt != null`이면 `notification/`에 알림 등록 위임.

---

#### `PATCH /api/applications/{id}`
필드 부분 수정. 단계 이동·태그 변경·메모·마감 모두 이 엔드포인트 1개로 처리.

**요청 (모든 필드 옵셔널, 명시된 것만 변경)**
```json
{
  "stageId": 3,
  "tagIds": [5],
  "memo": "면접 일정 5/15",
  "deadlineAt": "2026-05-15T07:00:00Z",
  "priority": "NORMAL"
}
```

**검증**
- 카드 소유자 = 요청자. 아니면 **404 `APPLICATION_NOT_FOUND`** (403 안 씀 — 존재 여부 노출 방지)
- `stageId` 변경 시: 새 Stage가 같은 userId 소유여야 함
- `tagIds` 변경 시: 모든 ID가 요청자 소유 태그여야 함. M:N 갱신은 **delete-then-insert**가 아닌 **diff 후 추가/제거**(불필요한 row churn 방지)
- 빈 본문(`{}`)은 200 + 변경 없음 응답 (idempotent)

**부수 효과**
- `deadlineAt` 변경 → `notification/`에 알림 갱신 위임 (이전 deadlineAt의 알림 제거 + 새 시각 등록)
- `stageId`만 변경되어도 `updatedAt` 갱신 (드래그앤드롭 활동성 추적)

**응답 200**: 변경 후 `ApplicationResponse`.

---

#### `DELETE /api/applications/{id}`
카드 삭제.

**플로우**
1. `findByIdAndUserId` → 없으면 404
2. `notification/`에 알림 제거 위임
3. `schedule/`에 외부 캘린더 export 정리 위임 (있을 경우)
4. `applications` 행 삭제 → `application_tags`, `retrospectives`는 DB CASCADE로 자동 삭제

**응답 204** (No Content).

**왜 알림/외부 정리를 Service에서 명시적으로** — DB CASCADE는 외부 시스템(Redis, Google Calendar)을 모르므로 leak 방지를 위해 도메인 코드가 직접 호출.

---

### 3.2 회고 (이 도메인에서 트리거만)

#### `POST /api/applications/{id}/retrospectives`
회고 작성. **본 도메인은 라우팅만 하고 본문은 `retrospective/` 도메인이 처리.** 카드 소유자 검증 후 `RetrospectiveService.create(userId, applicationId, request)`로 위임.

(요청/응답 형태는 `retrospective/tech.md` 참조 — 별도 문서)

---

### 3.3 Stage

#### `GET /api/stages`
요청자의 Stage 목록 (displayOrder 오름차순).

#### `POST /api/stages`
요청 `{name, color, displayOrder?}`.
- `category`는 **무조건 `IN_PROGRESS`로 서버가 강제 설정**. 요청 본문에서 받지 않음.
- `displayOrder` 미지정 시 = `max(displayOrder) + 1`. 지정 시 그 위치에 삽입하고 이후 행을 +1 씩 이동 (단일 트랜잭션).

#### `PATCH /api/stages/{id}`
`{name?, color?, displayOrder?}` 부분 수정. `category`는 변경 불가 (요청에 있어도 무시).
- `displayOrder` 변경은 **순서 swap 아닌 reorder**: 클라이언트가 새 위치만 보내면 서버가 사이 행을 +1 또는 -1 시킴.

#### `DELETE /api/stages/{id}`
- `category in (PASSED, REJECTED)` → **409 `STAGE_FIXED`**
- 카드 ≥1개 → **409 `STAGE_NOT_EMPTY`** (자동 이동/CASCADE 안 함, 사용자에게 명시적 정리 강제)
- 그 외 → 삭제

---

### 3.4 Tag

| Method | Path | 동작 |
|---|---|---|
| GET | `/api/tags` | 요청자의 태그 목록 |
| POST | `/api/tags` | `{name, color}`. `(user_id, name)` 중복 시 **409 `TAG_DUPLICATE`** |
| PATCH | `/api/tags/{id}` | `{name?, color?}`. 이름 변경으로 중복 발생 시 동일 |
| DELETE | `/api/tags/{id}` | hard delete. `application_tags`는 DB CASCADE |

---

## 4. 핵심 플로우

### 4.1 카드 단계 이동 (드래그앤드롭)

```
프론트: PATCH /api/applications/101  body: { stageId: 3 }
   ↓
Controller: @PreAuthorize + @CurrentUser → ApplicationService.update(userId=99, id=101, request)
   ↓
Service (@Transactional)
  ├─ applicationRepository.findByIdAndUserId(101, 99)   → null이면 APPLICATION_NOT_FOUND
  ├─ stageRepository.findByIdAndUserId(3, 99)            → null이면 STAGE_NOT_FOUND
  ├─ application.changeStage(3)                          (도메인 메서드: var stageId = 3)
  └─ (deadlineAt 변경 없음 → 알림 갱신 스킵)
   ↓
Hibernate dirty checking → UPDATE applications SET stage_id=3, updated_at=now() WHERE id=101
   ↓
Controller: ApiResponse.success(application.toResponse())
```

낙관적 락은 두지 않음 — 같은 카드를 두 사용자가 동시에 수정할 수 없고, 한 사용자가 두 탭에서 동시에 움직이는 충돌은 비즈니스적으로 허용 가능 (마지막 쓰기 승).

### 4.2 카드 삭제

```
DELETE /api/applications/101
   ↓
Service (@Transactional)
  ├─ findByIdAndUserId(101, 99) → APPLICATION_NOT_FOUND
  ├─ notificationQueue.removeByApplicationId(101)        ← Redis ZREM
  ├─ scheduleService.cancelExternalExport(101)            ← 외부 캘린더 export 있을 때
  └─ applicationRepository.delete(application)
       └ DB CASCADE: application_tags, retrospectives 자동 삭제
   ↓
204
```

**주의**: Redis 호출이 트랜잭션 롤백을 따라가지 않는다. DB 삭제 *후* Redis 삭제가 실패하면 마감 알림 좀비가 남는다. v1에서는 Redis 호출이 실패해도 무시하고 (로그만), 야간 정리 잡이 sweep — 또는 **DB 삭제 *전* Redis 삭제** 후 DB 실패 시 Redis 키를 다시 등록하는 보상 로직. v1은 단순화: **Redis 먼저 삭제 → DB 삭제. Redis 실패는 예외 던져 전체 롤백**(가용성 < 정합성). 차후 sweep 잡 도입 시 정책 재검토.

### 4.3 Stage 삭제 (보호 분기)

```
DELETE /api/stages/{id}
   ↓
Service
  ├─ findByIdAndUserId → STAGE_NOT_FOUND
  ├─ category가 PASSED/REJECTED → STAGE_FIXED (409)
  ├─ countByUserIdAndStageId > 0 → STAGE_NOT_EMPTY (409)
  └─ delete
```

`countByUserIdAndStageId`는 인덱스 `applications(user_id, stage_id)`로 빠름.

### 4.4 카드 등록 + 마감 알림 등록

```
POST /api/applications  body: { ..., deadlineAt: "2026-05-10T14:00:00Z" }
   ↓
Service (@Transactional)
  ├─ stageRepository.findByIdAndUserId(stageId, userId) → STAGE_NOT_FOUND
  ├─ tagRepository.findAllByIdInAndUserId(tagIds, userId)
  │     → 누락된 ID 있으면 TAG_NOT_FOUND
  ├─ Application 생성 + save → id 발급
  ├─ application_tags 생성 (M:N batch insert)
  └─ notificationQueue.enqueue(applicationId, deadlineAt - 3d, 1d, 0d)
   ↓
201
```

`notificationQueue.enqueue`는 `notification/` 도메인의 인터페이스 메서드. 이 도메인은 그 시그니처만 알고 구현은 모른다.

---

## 5. 검증 규칙 매트릭스

| 필드 | 규칙 | 위반 시 코드 |
|---|---|---|
| `companyName`, `position` | NotBlank, 1..100 | `INVALID_INPUT` |
| `stageId` | 존재 + 요청자 소유 | `STAGE_NOT_FOUND` (404) |
| `tagIds[*]` | 모두 존재 + 요청자 소유 | `TAG_NOT_FOUND` (404) |
| `priority` | enum 값 | `INVALID_INPUT` |
| `noResponseDays` | 1..365 | `INVALID_INPUT` |
| `memo` | nullable, ≤ 5000자 | `INVALID_INPUT` |
| `jobPostingUrl` | nullable, URL 형식 | `INVALID_INPUT` |
| `tag.name` | NotBlank, 1..30 | `INVALID_INPUT` |
| `tag.color`, `stage.color` | hex `#RRGGBB` | `INVALID_INPUT` |
| `stage.name` | NotBlank, 1..30 | `INVALID_INPUT` |
| `stage.displayOrder` | 0..999 | `INVALID_INPUT` |

---

## 6. ErrorCode

추가될 코드 (`global/exception/ErrorCode.kt`):

```kotlin
APPLICATION_NOT_FOUND(NOT_FOUND, "APPLICATION_NOT_FOUND", "지원 정보를 찾을 수 없습니다"),
STAGE_NOT_FOUND(NOT_FOUND, "STAGE_NOT_FOUND", "단계를 찾을 수 없습니다"),
STAGE_FIXED(CONFLICT, "STAGE_FIXED", "최종합격/불합격 단계는 삭제할 수 없습니다"),
STAGE_NOT_EMPTY(CONFLICT, "STAGE_NOT_EMPTY", "단계에 카드가 남아 있어 삭제할 수 없습니다"),
TAG_NOT_FOUND(NOT_FOUND, "TAG_NOT_FOUND", "태그를 찾을 수 없습니다"),
TAG_DUPLICATE(CONFLICT, "TAG_DUPLICATE", "이미 존재하는 태그 이름입니다"),
```

기본 `INVALID_INPUT`, `UNAUTHORIZED`, `FORBIDDEN`은 `global/`에 이미 있다고 가정.

---

## 7. 트랜잭션 경계

- **모든 변경(POST/PATCH/DELETE) Service 메서드는 `@Transactional`** — Hibernate dirty checking, M:N 갱신, 알림 큐 호출이 한 단위.
- **클래스 레벨 `@Transactional(readOnly = true)`** — 조회는 readOnly, 변경 메서드만 위에서 재선언.
- **외부 시스템 호출(Redis, 외부 캘린더)은 트랜잭션 안에 둔다** — 실패 시 전체 롤백. `@TransactionalEventListener(AFTER_COMMIT)`로 빼면 정합성 깨질 때 복구 어려움. 트레이드오프: 외부 시스템 지연이 DB 락 시간을 늘림. 마감 알림 등록은 Redis ZADD 1ms 수준이라 허용.
- **Application 등록 시 M:N tag insert는 `saveAll`로 batch** — `hibernate.jdbc.batch_size=50` 적용 (global config).

---

## 8. 동시성 고려

| 시나리오 | 위험 | 대응 |
|---|---|---|
| 같은 카드를 두 탭에서 동시에 PATCH | 일부 필드 덮어씀 | 마지막 쓰기 승 — 비즈니스적으로 허용 |
| 같은 Tag 이름을 두 요청이 동시에 POST | UNIQUE 위반으로 한 쪽 500 | DB UNIQUE에 의존, 캐치하여 `TAG_DUPLICATE` 변환 |
| Stage 삭제와 그 Stage에 카드 추가가 동시 | 카드가 deleted Stage 참조 | Service에서 `count > 0`을 SELECT FOR UPDATE 없이 검사하므로 race 가능. v1에서는 허용 — 다음 카드 조회 시 stage join 결과로 노출되며, 사용자는 카드를 다시 다른 Stage로 이동 가능. v2에서 `SELECT ... FOR UPDATE` 도입 검토 |
| Stage displayOrder 동시 reorder | 중간 행 +1/-1이 꼬임 | 사용자당 Stage 5~20개 수준. 단일 사용자가 동시에 두 reorder를 보내는 일이 드물어 v1에서는 단순 update로 처리. 이상 발생 시 GET → 재정렬로 복구 가능 |

---

## 9. 보안 체크리스트

- [ ] 모든 Repository 메서드가 `userId` 조건을 포함 (Spec 작성 시 grep 자동화)
- [ ] 모든 Controller에 `@PreAuthorize("isAuthenticated()")` + `@CurrentUser`
- [ ] 외부 노출 응답에 `userId` 미포함 (자기 자신만 다루므로 불필요)
- [ ] 404 vs 403 — 다른 사용자 리소스 조회는 항상 **404**(존재 노출 방지)
- [ ] `@Valid`로 Bean Validation 강제, 누락 시 `INVALID_INPUT`
- [ ] `memo`, `jobPostingUrl`은 사용자 입력 그대로 저장 — XSS는 프론트 렌더링 책임. 서버 측에서는 길이 제한만

---

## 10. 테스트 시나리오

### 10.1 단위 (MockK, ApplicationServiceTest)

- `getBoard` 빈 보드도 모든 Stage가 응답에 포함
- `create` 성공 — `notification.enqueue` 호출 검증
- `create`: 다른 사용자의 stageId → `STAGE_NOT_FOUND`
- `create`: 일부 tagIds가 다른 사용자 소유 → `TAG_NOT_FOUND` (전체 롤백)
- `update`: 다른 사용자 카드 → `APPLICATION_NOT_FOUND`
- `update`: stageId만 변경 시 알림 갱신 스킵, deadlineAt 변경 시 갱신 호출
- `update`: 빈 body → 200 + 변경 없음
- `delete`: notification/schedule 정리 호출 후 카드 삭제 순서
- `delete`: Redis 실패 시 트랜잭션 롤백 (DB 카드 유지)
- `deleteStage`: PASSED/REJECTED → `STAGE_FIXED`
- `deleteStage`: 카드 1개 이상 → `STAGE_NOT_EMPTY`
- `createTag`: 같은 이름 중복 → `TAG_DUPLICATE`

### 10.2 통합 (Testcontainers)

- 회원가입 → `StageSeedService.seedDefault` 호출 결과 5개 Stage 시드 검증
- 칸반 보드 GET: 빈 Stage 포함, M:N 태그 fetch join N+1 없음 — `@DataJpaTest` + `Statistics` 어서션
- 카드 생성 → 마감 1분 후로 변경 → Redis ZSCORE 변화 확인
- 카드 삭제 → application_tags / retrospectives 동시 사라짐 (DB CASCADE)
- Stage 삭제 분기 3가지 (FIXED / NOT_EMPTY / 성공) 각 케이스
- Tag 동일 이름 두 번 POST → 두 번째 `TAG_DUPLICATE`
- 다른 사용자의 카드/Stage/Tag에 대한 모든 엔드포인트 → 404 (IDOR 회귀 테스트)

### 10.3 부하 (선택)
- 사용자당 카드 500개 + 보드 GET p95 측정 (목표 ≤ 200ms)

---

## 11. 마이그레이션

Flyway 또는 Liquibase로 다음 순서로 적용:

```
V1__create_users.sql                  # auth/ 도메인이 먼저
V2__create_stages.sql
V3__create_tags.sql
V4__create_applications.sql           # stage_id는 FK 없이 INT
V5__create_application_tags.sql       # FK + UNIQUE
V6__index_applications_user_stage.sql
V7__index_applications_user_deadline.sql
```

각 V 파일은 idempotent하지 않다 — Flyway 표준 그대로 사용.

---

## 12. 미해결/v2 후보

| 항목 | 현재 결정 | v2 검토 사유 |
|---|---|---|
| 다중 태그 AND/OR 필터 | v1 단일 태그만 | UI 요구 발생 시 추가 |
| Application soft delete | hard delete | 회고 보존 요구 시 도입 |
| Stage reorder 동시성 잠금 | 없음 | 동시성 충돌 빈번 시 `SELECT FOR UPDATE` |
| Stage 삭제 시 카드 자동 이동 옵션 | 차단만 함 | UX 요청 시 "이동 후 삭제" 단일 트랜잭션 도입 |
| 카드 검색(텍스트) | 없음 | 카드 100개 초과 사용자 발생 시 (회사명/메모 LIKE 또는 FULLTEXT) |
| 통계(합격률 등) | 없음 | `PASSED`/`REJECTED` 시드 카테고리를 활용해 별도 도메인으로 분리 |

---

## 13. 구현 순서 제안

1. `global/` 인프라가 없는 항목 먼저 — `BaseEntity`, `ApiResponse`, `ErrorCode`, `GlobalExceptionHandler`, `@CurrentUser`, JWT 필터
2. Flyway 마이그레이션 V2~V7 작성, Testcontainers로 적용 검증
3. `Stage` 엔티티 + Repository + Service (시드 포함) + Controller + 단위 테스트
4. `Tag` 엔티티 + CRUD + 중복 핸들링
5. `Application` + `ApplicationTag` 엔티티 + Repository (보드 조회 fetch join) + Service
6. Application Controller: GET 보드 → POST → PATCH → DELETE 순
7. `notification/`의 인터페이스만 먼저 stub으로 만들고 호출 (실 구현은 다른 도메인 PR)
8. 통합 테스트(Testcontainers) — IDOR 회귀, CASCADE, M:N
9. CLAUDE.md의 도메인 요약·엔드포인트 표 갱신, 본 tech.md의 §12 미해결 항목 재정리

---

## 14. 미구현 현황 (스냅샷: 2026-04-30)

본 도메인의 1차 골격(Stage/Application/Tag CRUD)은 들어갔으나, 다른 도메인·인프라에 묶인 항목과 운영 품질 항목이 남아 있다. PR 단위로 이 표를 갱신한다.

### 14.1 본 도메인 내부 — 즉시 처리 가능

| 항목 | 위치 | 상태 | 비고 |
|---|---|---|---|
| `Application` 도메인 메서드 (`changeStage`, `updateMemo`, `setDeadline`) | `domain/Application.kt` | ❌ 미구현 | 현재 Service에서 setter 직접 조작. 도메인 캡슐화를 위해 메서드로 이동 필요. `setDeadline`은 알림/스케줄 동기화 트리거 포인트가 됨 |
| Stage `displayOrder` 삽입·이동 시 사이 행 reorder | `service/StageService.kt` | ❌ 미구현 | 현재 `create`는 단순 `max+1`, `update`는 받은 값 그대로 저장. `tech.md §3.3`에 정의된 "사이 행 +1/-1 단일 트랜잭션" 미적용 — 동일 `displayOrder` 충돌 가능 |
| `TAG_DUPLICATE` UNIQUE 위반 변환 | `service/TagService.kt` | ❌ 미구현 | DB `(user_id, name)` UNIQUE에 의존 예정이나, `DataIntegrityViolationException` → `BusinessException(TAG_DUPLICATE)` 변환 로직 없음. 현재는 500으로 누수 |
| 빈 PATCH(`{}`) idempotent 응답 명시 | `service/ApplicationService.kt:91` | ⚠️ 부분 | 동작은 되지만 명시적 단축 경로 없음 — 현 구현은 변경 없는 entity를 dirty checking이 그냥 skip |
| 보드 조회 N+1 검증 | `service/ApplicationService.kt:33` | ❌ 미구현 | `findBoardCards` + `findTagViewsByApplicationIds`로 2 쿼리. fetch join 안 쓰고 IN 절로 묶음 — 통합 테스트로 쿼리 수 회귀 방지 필요 |
| `meta` (`requestId`, `timestamp`) 채우기 | `global/response/` | ❌ 미구현(추정) | `ApiResponse.success(data)`만 호출 — 트레이싱·디버깅용 메타 미주입 |

### 14.2 다른 도메인 의존 — 해당 도메인 도입 시 이 도메인 코드 수정 필요

| 항목 | 위치 | 상태 | 트리거 |
|---|---|---|---|
| `notification/` 위임 — 카드 생성/마감 변경/카드 삭제 시 Redis 알림 큐 동기화 | `service/ApplicationService.kt:128, 155` | ❌ TODO 주석만 | `notification/` 도메인의 `notificationQueue` 인터페이스 도입 후 호출 |
| `schedule/` 위임 — `deadlineAt` 변경 시 `JOB_POSTING ScheduleEvent` 동기화, 삭제 시 정리 | `service/ApplicationService.kt:128, 153` | ❌ TODO 주석만 | `schedule/` 도메인의 `ScheduleEventRepository`/`CalendarExporter` 도입 후 호출 |
| `retrospective/` 라우팅 — `POST /api/applications/{id}/retrospectives` | `controller/ApplicationController.kt` | ❌ 미구현 | `retrospective/` 도메인의 `RetrospectiveService` 도입 후 카드 소유자 검증 + 위임 |
| `StageSeedService.seedDefault(userId)` — 가입 시 5개 기본 Stage 시드 | `service/` | ❌ 미구현 | `user/`(또는 `auth/`) 회원가입 플로우에서 호출. 현재는 사용자가 Stage 0개 상태에서 시작하므로 카드 생성 불가 — **신규 가입 사용자 차단 위험** |
| `auth/` JWT + `@CurrentUser` AOP 도입 | 모든 Controller | ❌ 미구현 | 현재 `@RequestHeader("X-User-Id") userId: Long` 스텁 사용 (`ApplicationController.kt:35`, `StageController.kt:31`, `TagController` 동일). **운영 환경 노출 금지 — 임의 userId 위조 가능** |
| `ai/` Suggestion 수락 시 `applySuggestion` 진입점 | `service/ApplicationService.kt` | ❌ 미구현 | AI가 `Application` 직접 변경 금지 정책상, 수락 시 호출될 메서드 시그니처만 미리 잡아둘 것 |

### 14.3 인프라 / 횡단 관심사

| 항목 | 상태 | 비고 |
|---|---|---|
| Flyway 마이그레이션 V2~V7 | ❌ 미구현 | 현재 `ddl-auto=update`로 추정. 운영 전 SQL 마이그레이션으로 전환, `application_tags(application_id, tag_id) UNIQUE`·CASCADE FK가 실제 DB에 적용되었는지 검증 필요 |
| `BaseEntity.deletedAt` soft delete | ⚠️ 보류 | tech.md §2 결정대로 v1 hard delete. 회고 보존 요구 발생 시 재검토 |
| 통합 테스트 (Testcontainers) | ❌ 미구현 | 현재 단위 테스트(`ApplicationServiceTest`, `TagServiceTest`)만 존재. tech.md §10.2의 IDOR 회귀·CASCADE·M:N 시나리오 미커버 |
| 부하 테스트 (카드 500개 보드 GET p95 ≤ 200ms) | ❌ 미구현 | 도메인 안정화 후 |
| Rate Limit (로그인 5회/분, 메일 AI 100회/일) | ❌ 미구현 | `global/` 책임. 본 도메인 무관하지만 운영 체크리스트로 기록 |
| 응답 본문 XSS 방어 | ⚠️ 프론트 책임으로 위임 | `memo`, `jobPostingUrl` 등 사용자 입력은 그대로 저장. 서버 길이 제한만 적용됨 |

### 14.4 명시적 비범위 (이 도메인이 처리하지 않음)

- 회고 본문 처리 — `retrospective/` 도메인 책임
- AI 메일 분류 → 단계 변경 제안 — `ai/`, `mail/` 도메인이 `Suggestion` 생성, 본 도메인은 수락 후 도메인 메서드 호출만
- iCalendar export — `export/` 도메인
- 마감 알림 발송 잡 — `global/scheduler/`
