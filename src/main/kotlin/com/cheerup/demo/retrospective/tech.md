# retrospective 도메인 — 기술 스펙 (tech.md)

이 문서는 `retrospective/` 도메인(단계별/종합 회고 + 사용자 질문 템플릿)의 **구현 진입 직전 설계 명세**다. 도메인 모델·정책의 *왜*는 [`CLAUDE.md`](./CLAUDE.md)에 있다. 본 문서는 그것을 받아 *어떻게 동작하는지* — API 계약, 플로우, 검증, 트랜잭션, 테스트 — 를 정의한다.

> 깊이: **설계 수준**. 필드 시그니처·메서드 본문은 구현 단계에서 결정하되, 본 문서가 정한 입출력 형태·플로우·에러 코드는 그대로 따른다.

---

## 1. Scope

### In Scope (이 도메인이 책임짐)
- `Retrospective`, `RetrospectiveTemplate` 두 엔티티의 영속화·조회·변경
- 회고 CRUD (빈 회고 생성 → 항목 누적 → 요약 작성 → 삭제)
- 회고 항목(Q&A 쌍) 단위 조작 — append, 인덱스 기반 update/delete
- 템플릿 CRUD 및 회고에 템플릿 적용 (스냅샷 복사)
- AI 질문 생성 — `Application`/`Stage` 컨텍스트를 **별도 AI 서버**에 전달해 회고 질문 목록을 동기 응답 (DB 미반영)

### Out of Scope (다른 도메인이 처리)
| 책임 | 위임 대상 |
|---|---|
| `Application` 소유권 검증의 원본 | `application/` (요청 들어온 `applicationId`의 userId 일치 확인은 본 도메인에서 직접 lookup) |
| `Stage` 라벨/카테고리 조회 | `stage_id` 스냅샷만 보존, 표시용 정보는 조회 시 `application/`을 join하거나 응답에서 ID만 노출 |
| 마감 알림·달력 표시 | `notification/`, `schedule/` (회고는 알림/달력과 무관) |
| AI 메일 분류 → Suggestion | `ai/`, `mail/` (`Suggestion` 패턴은 비동기·승인 흐름이라 별개. 회고 AI는 동기 보조) |
| AI 모델 실행/프롬프트 관리 | 별도 AI 서버 (`POST /ai/retrospective/questions`). 백엔드는 HTTP 클라이언트와 응답 검증만 책임 |
| 사용자 컨텍스트 / JWT | `global/jwt`, `@CurrentUser` |

### 비기능 요구
- 회고/템플릿 CRUD: **p95 ≤ 200ms**
- AI 질문 생성: 백엔드 EC2 → 별도 AI 서버 왕복으로 **p95 ≤ 6s** 허용 (UI는 로딩 표시). 타임아웃 8s 후 `AI_GENERATION_TIMEOUT` 반환
- 모든 엔드포인트 IDOR 방지 — 요청 `userId`와 리소스 소유자 일치 검증 필수
- 본 도메인 모든 row는 hard delete (회고는 `Application` CASCADE로만 정리됨)

---

## 2. Data Model 개요

세부 필드는 `CLAUDE.md` §엔티티 참조. 본 절은 **저장소 계층의 의사결정**만 다룬다.

### 패키지 배치

```
retrospective/
├── controller/    # RetrospectiveController, RetrospectiveTemplateController
├── service/       # RetrospectiveService, RetrospectiveTemplateService, RetrospectiveQuestionService
├── repository/    # RetrospectiveRepository, RetrospectiveTemplateRepository
├── domain/        # Retrospective, RetrospectiveItem(값 객체), RetrospectiveTemplate
├── ai/            # RetrospectiveQuestionGenerator interface + External/Mock 구현
└── dto/           # *Request, *Response data class
```

`RetrospectiveItem`은 값 객체(`data class`)로 `domain/`에 두되 별도 엔티티가 아니다 — `Retrospective.items` JSON 컬럼에 직렬화된다.

### 테이블/제약 요약

| 테이블 | PK | 유니크 | 외래키 (DB 레벨) |
|---|---|---|---|
| `retrospectives` | `id` | — | `user_id → users(id) ON DELETE CASCADE`<br>`application_id → applications(id) ON DELETE CASCADE`<br>`stage_id`는 **FK 미설정** (스냅샷, dangling 허용) |
| `retrospective_templates` | `id` | `(user_id, name)` | `user_id → users(id) ON DELETE CASCADE` |

**`retrospectives.stage_id`에 DB FK를 걸지 않는 이유** — `CLAUDE.md` §삭제 정책에 명시. Stage 삭제 후에도 회고는 보존되어야 하므로 FK CASCADE/SET NULL 모두 부적합. 조회 시 stage join 결과가 null이면 "삭제된 단계"로 표시.

**`application_id`는 DB FK + CASCADE** — `application/` 도메인이 카드 삭제 시 회고를 명시적으로 정리하지 않고 DB에 위임 (`application/CLAUDE.md` 삭제 정책과 일치).

### 인덱스

| 인덱스 | 사용처 |
|---|---|
| `retrospectives(application_id)` | 카드별 회고 목록 조회 |
| `retrospectives(user_id, stage_id)` | "면접 단계 회고 모아보기" (Phase 2 통계 포함) |
| `retrospective_templates(user_id)` | 사용자 템플릿 목록 |
| `retrospective_templates(user_id, name)` UNIQUE | 같은 사용자 내 템플릿 이름 중복 방지 |

### JSON 컬럼

- `retrospectives.items` — `List<RetrospectiveItem>` 직렬화. `MEDIUMTEXT` 또는 MySQL `JSON` 타입.
- `retrospective_templates.questions` — `List<String>` 직렬화. 동일.

JPA `AttributeConverter`로 처리. `global/persistence/`에 다음 두 컨버터를 추가한다 (현재 미존재 — `JsonMapConverter` 패턴 참조):

```kotlin
@Converter class RetrospectiveItemListConverter : AttributeConverter<List<RetrospectiveItem>, String>
@Converter class StringListConverter : AttributeConverter<List<String>, String>
```

내부에서 `jackson-module-kotlin` 사용. null/빈 컬럼은 `emptyList()`로 복원.

### 낙관적 잠금

`Retrospective`에 `@Version` 컬럼을 둔다. 이유는 §8.

`RetrospectiveTemplate`에는 두지 않는다 — 템플릿은 사용자가 손으로 편집하는 빈도가 낮고 동시성 충돌이 사실상 없음.

### Soft delete

본 도메인은 모두 hard delete. `BaseEntity.deletedAt`은 두되 미사용.

---

## 3. API 계약

공통 규약은 `application/tech.md` §3과 동일. 인증·응답 포맷·시간 표기·페이지네이션 모두 같음. 회고도 사용자당 수십~수백 건 수준이라 페이지네이션 없음.

### 3.1 회고 (Retrospective)

#### `GET /api/applications/{appId}/retrospectives`
카드의 회고 목록. `Application` 소유 검증 후 `application_id` 인덱스로 조회.

**응답 200**
```json
{
  "data": {
    "retrospectives": [
      {
        "id": 12, "applicationId": 101, "stageId": 5,
        "itemCount": 7,
        "createdAt": "2026-04-21T03:00:00Z", "updatedAt": "2026-04-22T01:10:00Z"
      }
    ]
  },
  "meta": { ... }
}
```

목록 응답에는 `items` 본문을 싣지 않는다 (큰 JSON). 단건 GET에서만 풀어서 반환.

---

#### `GET /api/retrospectives/{id}`
회고 단건. `items` 포함.

**응답 200**
```json
{
  "data": {
    "id": 12, "applicationId": 101, "stageId": 5,
    "items": [
      { "question": "잘한 점은?", "answer": "라이브 코딩 침착하게 풀이" },
      { "question": "아쉬운 점은?", "answer": null }
    ],
    "createdAt": "...", "updatedAt": "..."
  }
}
```

---

#### `POST /api/applications/{appId}/retrospectives`
빈 회고 생성. `items`는 항상 빈 리스트로 시작.

**요청**
```json
{ "stageId": 5 }
```

**검증**
- `Application` 소유자 = 요청자 (`APPLICATION_NOT_FOUND` 404)
- `stageId` 옵션. 값이 있으면 같은 `userId` 소유 Stage여야 함 (`STAGE_NOT_FOUND` 404). null이면 종합 회고

**응답 201**: 생성된 `RetrospectiveResponse` (items=[])

> 본 엔드포인트의 라우팅은 `application/` 도메인이 받고 본 도메인 Service로 위임 — `application/tech.md §3.2` 참조. 본 문서는 위임 이후의 본 도메인 로직을 기준으로 명세한다.

> **메타 PATCH 엔드포인트 없음.** `applicationId`/`stageId` 는 val 스냅샷이라 변경 불가, 별도 메타 필드(요약·제목 등)도 두지 않는다 — 회고의 컨텐츠는 `items` 가 전부이고 그것은 §3.2 항목 단위 엔드포인트로만 다룬다.

---

#### `DELETE /api/retrospectives/{id}`
회고 삭제. 외부 시스템 정리 없음 — 단순 row delete.

**플로우**
1. `findByIdAndUserId` → 없으면 404
2. `retrospectiveRepository.delete(r)`

**응답 204**.

---

### 3.2 회고 항목 (Q&A 쌍)

`items`는 JSON 리스트이므로 **인덱스 기반 조작**. 안정 ID는 v2 후보(§12).

#### `POST /api/retrospectives/{id}/items`
1건 append.

**요청**
```json
{ "question": "면접관 인상은?", "answer": null }
```

**검증**
- 회고 소유자 = 요청자
- `question`: NotBlank, 1..1000자
- `answer`: nullable, ≤ 5000자

**응답 200**: `{ items: [...전체 리스트...], version: 7 }` — 클라이언트가 다음 PATCH/DELETE 인덱스를 신뢰성 있게 쓸 수 있게 전체 items와 `@Version` 값을 함께 반환.

---

#### `PATCH /api/retrospectives/{id}/items/{index}`
인덱스 위치 항목 수정. `question`/`answer` 둘 다 또는 일부.

**요청**
```json
{ "answer": "차분하고 친절하셨음" }
```

**검증**
- 회고 소유자 = 요청자
- `index in 0..items.lastIndex` — 위반 시 **`RETROSPECTIVE_ITEM_INDEX_INVALID` (404)**
- `question`이 본문에 있다면 NotBlank
- 빈 본문(`{}`)은 200 + 변경 없음

**응답 200**: 전체 items + version.

---

#### `DELETE /api/retrospectives/{id}/items/{index}`
인덱스 위치 항목 제거. 이후 항목들의 인덱스는 -1 씩 당겨짐.

**검증**
- 회고 소유자 = 요청자
- `index in 0..items.lastIndex` → 위반 시 `RETROSPECTIVE_ITEM_INDEX_INVALID` (404)

**응답 200**: 전체 items + version.

---

### 3.3 템플릿 (RetrospectiveTemplate)

#### `GET /api/retrospective-templates`
요청자의 템플릿 목록. 가벼운 응답 (`questions`도 포함 — 보통 ≤ 30개 질문).

#### `GET /api/retrospective-templates/{id}`
템플릿 단건.

#### `POST /api/retrospective-templates`
**요청**
```json
{ "name": "1차 면접 회고", "questions": ["잘한 점", "아쉬운 점", "다음에 시도할 것"] }
```

**검증**
- `name`: NotBlank, 1..50자, `(user_id, name)` UNIQUE → 위반 시 **`RETROSPECTIVE_TEMPLATE_DUPLICATE` (409)**
- `questions`: List, 0..50개 (빈 리스트 허용 — 나중에 채울 수 있게)
- 각 질문: NotBlank, ≤ 1000자. blank 항목은 서버에서 필터링

**응답 201**.

#### `PATCH /api/retrospective-templates/{id}`
`{name?, questions?}` 부분 수정. 검증은 POST와 동일. 이름 변경으로 중복 시 `RETROSPECTIVE_TEMPLATE_DUPLICATE`.

#### `DELETE /api/retrospective-templates/{id}`
hard delete. 이미 적용된 회고의 `items`에는 영향 없음 (스냅샷).

---

#### `POST /api/retrospectives/{id}/apply-template`
템플릿의 질문들을 회고 `items` **끝에 append**. 답변은 모두 `null`.

**UX 흐름**

회고 편집 화면에서 사용자가 "템플릿 가져오기"를 누르면 프론트가 두 호출을 차례로 발생시킨다:

```
GET  /api/retrospective-templates             ← 사용자 템플릿 전체 목록 (questions 포함)
       사용자가 1개 선택
POST /api/retrospectives/{id}/apply-template  body: { templateId }
```

여러 템플릿을 연달아 적용해도 항목이 누적된다 (덮어쓰기 아님).

**요청**
```json
{ "templateId": 3 }
```

**플로우**
1. `findRetrospectiveByIdAndUserId(retrospectiveId, userId)` → `RETROSPECTIVE_NOT_FOUND`
2. `findTemplateByIdAndUserId(templateId, userId)` → `RETROSPECTIVE_TEMPLATE_NOT_FOUND`
3. `retrospective.appendQuestions(template.questions)` (CLAUDE.md의 도메인 메서드)
4. dirty checking으로 UPDATE

**응답 200**: 전체 items + version.

> 회고와 템플릿이 같은 사용자 소유인지 모두 검증. `apply-template`은 본질적으로 회고 변경이므로 `@Version` 충돌 처리도 동일 적용.

---

### 3.4 AI 질문 생성

> 2026-06-02 업데이트: 실제 구현은 백엔드 내부 Spring AI 호출이나 mock 데이터가 아니라 **외부 AI 서버 API 호출**로 진행한다. `http://127.0.0.1:8002`는 로컬 개발에서만 사용할 수 있는 예외값이며, dev/prod는 환경변수로 주입한 AI 서버 base URL을 사용한다. 최신 구현 계획은 §15를 우선한다.

#### `POST /api/retrospectives/ai-questions`
컨텍스트(공고명·회사명·직무명·단계명·질문 수)를 외부 AI 서버에 넘겨 회고 질문 목록을 받아온다. **DB 미반영**.

**요청**
```json
{ "applicationId": 101, "stageId": 5, "questionCount": 4 }
```

**검증**
- `applicationId` 필수, 양수, 요청자 소유
- `stageId` 옵션, 있으면 요청자 소유. 없으면 Application의 현재 stage를 fallback으로 사용
- `questionCount` 옵션. 기본값 4, 허용 범위 1..15

**처리**
1. `Application` lookup — `jobPostingTitle`, `companyName`, `position` 추출
2. `stageId`가 있으면 해당 `Stage` lookup. 없으면 `Application.stageId`의 현재 stage를 조회하고, 그래도 없으면 `"전체 전형"`으로 대체
3. `AiRetrospectiveQuestionRequest`로 변환해 `{AI_RETROSPECTIVE_BASE_URL}/ai/retrospective/questions` 호출
4. 외부 AI 응답 검증 — `questions` 1..15개, 각 `question` NotBlank ≤ 1000자
5. AI 응답의 메타데이터를 camelCase 응답 DTO로 정규화해 클라이언트에 반환. **`Suggestion` 엔티티 저장 없음**

**응답 200**
```json
{
  "data": {
    "questionSetTitle": "1차 기술면접 회고 질문",
    "jobRole": "백엔드 개발자",
    "processStage": "1차 기술면접",
    "questions": [
      {
        "category": "technical_depth",
        "question": "카카오 백엔드 기술면접에서 가장 답변이 부족했던 기술 개념은 무엇이었나요?",
        "reason": "기술 보완 포인트를 찾기 위함입니다.",
        "priority": "high",
        "sourceTemplateIds": ["q_backend_interview_001"]
      }
    ]
  }
}
```

**오류**
- 4xx: 입력 검증 실패 (`INVALID_INPUT`, `APPLICATION_NOT_FOUND` 등)
- 429: Rate Limit 초과 — `RATE_LIMITED`
- 502: 외부 AI API 호출 실패 또는 응답 스키마 위반 — `AI_GENERATION_FAILED` (재시도 가능 표시)
- 504: 외부 AI API 타임아웃 (8s) — `AI_GENERATION_TIMEOUT`

**Rate Limit**
- 사용자별 **일 50회**. 메일 AI 100회/일과 별도 카운터. `global/`의 RateLimit 인프라(미구현 시 본 도메인 도입과 함께 stub) 사용.

**외부 AI 호출 가드레일**
- 백엔드는 프롬프트를 만들거나 모델을 직접 실행하지 않는다. 프롬프트/모델 관리는 AI 서버 책임이다.
- 백엔드는 공고명, 회사명, 직무명, 단계명, 질문 수만 전송한다. 메일 본문이나 회고 답변 본문은 전송하지 않는다.
- AI 서버 출력은 계약 JSON 스키마를 통과해야 한다. 파싱 실패, 필수 필드 누락, 유효 질문 0개는 `AI_GENERATION_FAILED`로 처리한다.
- dev/prod 설정에서 `127.0.0.1:8002`를 기본값으로 두지 않는다.

> **왜 `Suggestion` 엔티티를 안 쓰는가** — `Suggestion`은 비동기·다중 사용자 시간차 수락 흐름(메일 → 분류 → 알림 → 사용자가 며칠 후 수락) 모델. 회고 질문 생성은 사용자가 즉시 요청하는 동기 보조이고, 어차피 사용자가 프론트에서 골라 `POST /items`로 명시 추가하므로 "AI 결과 DB 직접 반영 금지" 원칙은 이미 준수됨.

---

## 4. 핵심 플로우

### 4.1 빈 회고 생성 → 항목 누적

```
프론트:
  POST /api/applications/101/retrospectives  body: { stageId: 5 }
    → 빈 Retrospective(id=12, items=[]) 생성

  POST /api/retrospectives/12/items  body: { question: "잘한 점?", answer: null }
    → items = [{q:"잘한 점?", a:null}], version: 1

  PATCH /api/retrospectives/12/items/0  body: { answer: "라이브 코딩 통과" }
    → items = [{q:"잘한 점?", a:"라이브 코딩 통과"}], version: 2

  POST /api/retrospectives/12/items  body: { question: "아쉬운 점?", answer: "..." }
    → items = [..., {q:"아쉬운 점?", a:"..."}], version: 3
```

### 4.2 항목 추가 (낙관적 락)

```
Service.addItem(userId=99, retrospectiveId=12, request) (@Transactional)
  ├─ retro = repo.findByIdAndUserId(12, 99) → RETROSPECTIVE_NOT_FOUND
  ├─ retro.addItem(RetrospectiveItem(req.question, req.answer))
  │     └ require(question.isNotBlank())
  └─ flush 시 @Version 충돌 → ObjectOptimisticLockingFailureException
       → 1회 재시도 (read-modify-write 다시 수행)
       → 두 번째도 충돌 시 RETROSPECTIVE_CONCURRENT_MODIFICATION (409)
```

### 4.3 템플릿 적용

```
POST /api/retrospectives/12/apply-template  body: { templateId: 3 }
  ↓
Service (@Transactional)
  ├─ retro = retroRepo.findByIdAndUserId(12, 99) → 404
  ├─ tpl   = tplRepo.findByIdAndUserId(3, 99)    → 404
  ├─ retro.appendQuestions(tpl.questions)
  └─ flush → @Version 충돌 시 4.2와 동일 재시도
```

`tpl.questions`의 문자열을 그 시점에 복사한 `RetrospectiveItem(question=..., answer=null)`로 추가. 이후 템플릿이 변경/삭제돼도 회고에는 영향 없음.

### 4.4 AI 질문 생성 (DB 미반영)

> 2026-06-02 업데이트: 아래 흐름의 `generator.generate(ctx)`는 mock 데이터 생성이나 백엔드 내부 모델 호출이 아니라 §15의 `ExternalRetrospectiveQuestionGenerator`가 외부 AI API를 호출하는 것으로 해석한다.

```
POST /api/retrospectives/ai-questions  body: { applicationId: 101, stageId: 5, questionCount: 4 }
  ↓
Service.generateQuestions(userId=99, request)  (no @Transactional — 읽기만 + 외부 호출)
  ├─ app   = appRepo.findByIdAndUserId(101, 99) → APPLICATION_NOT_FOUND
  ├─ stage = if (request.stageId != null)
  │     stageRepo.findByIdAndUserId(request.stageId, 99) → STAGE_NOT_FOUND
  │   else
  │     app.stageId?.let { stageRepo.findByIdAndUserId(it, 99) } ?: fallback("전체 전형")
  ├─ questionCount = request.questionCount ?: properties.defaultQuestionCount
  ├─ validate questionCount in 1..properties.maxQuestionCount
  ├─ rateLimiter.tryAcquire(userId, "retrospective-ai", limit=50/day) → RATE_LIMITED
  ├─ aiRequest = {
  │     user_id,
  │     job_posting_title,
  │     company_name,
  │     job_role,
  │     process_stage,
  │     question_count
  │   }
  ├─ result = generator.generate(aiRequest)       ← 외부 AI API 호출 (read timeout 8s)
  │     ├ 연결/HTTP/파싱 실패 → AI_GENERATION_FAILED (502)
  │     ├ 타임아웃 → AI_GENERATION_TIMEOUT (504)
  │     └ 스키마 위반/유효 질문 0개 → AI_GENERATION_FAILED
  └─ return RetrospectiveQuestionsResponse(result.toCamelCaseDto())
```

### 4.5 회고 삭제 / 카드 CASCADE

```
DELETE /api/retrospectives/12
  ↓
Service.delete(@Transactional)
  ├─ findByIdAndUserId → 404
  └─ delete(retro)

DELETE /api/applications/101  (다른 도메인)
  ↓
DB CASCADE → retrospectives 자동 삭제 (Service 명시 호출 없음)
```

`Stage` 삭제는 본 도메인에 영향 없음 — `stageId`는 dangling 허용. 조회 시 stage join 결과가 null이면 응답에 `"stageDeleted": true` 같은 표식 추가 가능 (Phase 2).

---

## 5. 검증 규칙 매트릭스

| 필드 | 규칙 | 위반 시 코드 |
|---|---|---|
| `RetrospectiveItem.question` | NotBlank, 1..1000자 | `INVALID_INPUT` |
| `RetrospectiveItem.answer` | nullable, ≤ 5000자 | `INVALID_INPUT` |
| 항목 인덱스 | `0..items.lastIndex` | `RETROSPECTIVE_ITEM_INDEX_INVALID` (404) |
| `RetrospectiveTemplate.name` | NotBlank, 1..50, `(user_id, name)` UNIQUE | `INVALID_INPUT` / `RETROSPECTIVE_TEMPLATE_DUPLICATE` (409) |
| `RetrospectiveTemplate.questions` | 0..50개, 각 질문 NotBlank ≤ 1000자 | `INVALID_INPUT` |
| `applicationId` (생성/AI) | 존재 + 요청자 소유 | `APPLICATION_NOT_FOUND` (404) |
| `stageId` (옵션) | 존재 + 요청자 소유 | `STAGE_NOT_FOUND` (404) |
| AI 호출 빈도 | 50회/일/사용자 | `RATE_LIMITED` (429) |

---

## 6. ErrorCode

`global/exception/ErrorCode.kt`에 추가:

```kotlin
RETROSPECTIVE_NOT_FOUND(NOT_FOUND, "RETROSPECTIVE_NOT_FOUND", "회고를 찾을 수 없습니다"),
RETROSPECTIVE_ITEM_INDEX_INVALID(NOT_FOUND, "RETROSPECTIVE_ITEM_INDEX_INVALID", "회고 항목 인덱스가 잘못되었습니다"),
RETROSPECTIVE_CONCURRENT_MODIFICATION(CONFLICT, "RETROSPECTIVE_CONCURRENT_MODIFICATION", "다른 변경과 충돌했습니다. 다시 시도해주세요"),
RETROSPECTIVE_TEMPLATE_NOT_FOUND(NOT_FOUND, "RETROSPECTIVE_TEMPLATE_NOT_FOUND", "회고 템플릿을 찾을 수 없습니다"),
RETROSPECTIVE_TEMPLATE_DUPLICATE(CONFLICT, "RETROSPECTIVE_TEMPLATE_DUPLICATE", "이미 존재하는 템플릿 이름입니다"),
AI_GENERATION_FAILED(BAD_GATEWAY, "AI_GENERATION_FAILED", "AI 응답 생성에 실패했습니다"),
AI_GENERATION_TIMEOUT(GATEWAY_TIMEOUT, "AI_GENERATION_TIMEOUT", "AI 응답이 시간 내에 도착하지 않았습니다"),
```

`APPLICATION_NOT_FOUND`, `STAGE_NOT_FOUND`, `RATE_LIMITED`, `INVALID_INPUT`, `UNAUTHORIZED`는 다른 도메인이 이미 등록한다고 가정.

---

## 7. 트랜잭션 경계

- **모든 변경 Service 메서드 `@Transactional`** — `Retrospective`/`Template`의 dirty checking과 JSON 컬럼 직렬화가 한 단위.
- **클래스 레벨 `@Transactional(readOnly = true)`** — 조회는 readOnly, 변경은 위에서 재선언.
- **AI 질문 생성은 트랜잭션 없음** — DB write 없음. `Application`/`Stage` 조회는 별도 readOnly 트랜잭션 또는 트랜잭션 밖 단일 SELECT. 외부 AI API 호출이 트랜잭션을 잡지 않게 주의 (장기 트랜잭션 방지).
- **Rate Limiter 갱신**은 외부 AI API 호출 *전*에 카운터 +1, 호출 실패 시에도 차감하지 않음 (단순화). AI 호출 비용 보호가 목적이므로 호출되지 않은 케이스(검증 실패)도 카운트 안 하도록 주의 — 검증 통과 직후 한 번만 acquire.
- **JSON 컬럼 갱신은 전체 컬럼 재기록**이다 — `items` 변경 시 row 전체 UPDATE. v1 규모(items 평균 ≤ 30개, ≤ 수십 KB)에서는 무시.

---

## 8. 동시성 고려

| 시나리오 | 위험 | 대응 |
|---|---|---|
| **같은 회고에 두 탭에서 동시에 항목 append** | T1, T2가 같은 items 읽고 각자 append → save → 하나 유실 | **`@Version` 낙관적 락 + 1회 재시도**. 재시도도 실패하면 `RETROSPECTIVE_CONCURRENT_MODIFICATION` (409) — 클라이언트가 GET 후 다시 시도 |
| 인덱스 기반 PATCH/DELETE 도중 다른 탭에서 항목 추가/삭제 | 인덱스가 의도한 항목과 다른 항목을 가리킴 | `@Version` 충돌로 한 쪽이 거부됨. 인덱스 안정성은 보장 못 하므로 클라이언트는 응답의 새 `items`로 화면 갱신 |
| 같은 템플릿 이름을 두 요청이 동시에 POST | UNIQUE 위반 | DB UNIQUE에 의존. `DataIntegrityViolationException` → `BusinessException(RETROSPECTIVE_TEMPLATE_DUPLICATE)` 변환 |
| AI 호출 중 회고가 삭제됨 | AI 결과를 받아도 회고가 없음 | AI 응답은 회고와 무관(회고에 직접 쓰지 않음). 사용자가 받은 questions를 `POST /items`로 추가하려 하면 `RETROSPECTIVE_NOT_FOUND` |

**왜 낙관적 락인가** — 단일 사용자 도메인이라 충돌 자체가 드물다(두 탭 동시 편집 정도). 외부 AI 호출과 DB 편집 트랜잭션을 섞지 않으므로, 빠른 두 번 클릭 같은 흔한 케이스만 방어하면 충분.

---

## 9. 보안 체크리스트

- [ ] 모든 Repository 메서드가 `userId` 조건 포함
- [ ] 모든 Controller에 `@PreAuthorize("isAuthenticated()")` + `@CurrentUser`
- [ ] 다른 사용자 리소스 조회 시 항상 **404** (존재 노출 방지)
- [ ] AI 호출 전 `applicationId`/`stageId` 소유 검증 — 다른 사용자 정보가 외부 AI 서버에 새지 않게
- [ ] 외부 AI 서버에는 공고명/회사명/직무명/단계명/질문 수만 전달하고, 메일 본문·회고 답변 본문은 전달하지 않음
- [ ] AI 응답은 JSON 스키마 검증 후 사용
- [ ] `question`, `answer`, `template.name`, `template.questions[*]`은 사용자 입력 그대로 저장 — XSS는 프론트 렌더링 책임. 서버는 길이 제한만
- [ ] AI 호출 Rate Limit 적용 (50/일/사용자)

---

## 10. 테스트 시나리오

### 10.1 단위 (MockK)

**RetrospectiveServiceTest**
- `create`: 빈 회고로 생성, items=[]
- `create`: 다른 사용자 application → `APPLICATION_NOT_FOUND`
- `create`: 다른 사용자 stageId → `STAGE_NOT_FOUND`
- `addItem`: question blank → `INVALID_INPUT`
- `updateItem`: index 음수/초과 → `RETROSPECTIVE_ITEM_INDEX_INVALID`
- `updateItem`: 빈 본문 → 200 + 변경 없음
- `deleteItem`: index 초과 → `RETROSPECTIVE_ITEM_INDEX_INVALID`
- `delete`: 다른 사용자 회고 → `RETROSPECTIVE_NOT_FOUND`
- `applyTemplate`: 템플릿 questions가 회고 items 끝에 append (스냅샷 — 템플릿 객체와 동일성 깨져 있음)
- `applyTemplate`: 다른 사용자 템플릿 → `RETROSPECTIVE_TEMPLATE_NOT_FOUND`

**RetrospectiveTemplateServiceTest**
- `create`: 같은 이름 두 번 → `RETROSPECTIVE_TEMPLATE_DUPLICATE`
- `update`: 이름 변경으로 중복 → `RETROSPECTIVE_TEMPLATE_DUPLICATE`
- `create`: questions blank 항목 자동 필터링
- `delete`: 이미 적용된 회고에 영향 없음 (다른 도메인 영향 없음 단위로 검증)

**RetrospectiveQuestionServiceTest** (외부 AI client mock)
- `generate`: Rate Limit 초과 → `RATE_LIMITED`
- `generate`: 다른 사용자 application → `APPLICATION_NOT_FOUND`
- `generate`: `questionCount` 기본값/범위 검증
- `generate`: `stageId` 생략 시 Application의 현재 stage fallback
- `generate`: AI 서버가 잘못된 JSON 반환 → `AI_GENERATION_FAILED`
- `generate`: AI 서버 타임아웃 → `AI_GENERATION_TIMEOUT`
- `generate`: 정상 응답에서 blank/너무 긴 질문 필터링
- `ExternalRetrospectiveQuestionGenerator`: 요청 URL, 헤더, snake_case body, camelCase 응답 매핑 검증

### 10.2 통합 (Testcontainers — MySQL + Redis)

- 회고 생성 → 항목 5개 누적 → 단건 GET으로 items 5개 확인
- `Application` 삭제 → 회고도 DB CASCADE로 사라짐 (`retrospectives` count = 0)
- `Stage` 삭제 → 회고는 보존되고 `stageId`는 그대로 (dangling 허용)
- 템플릿 생성 → 회고에 apply → 템플릿 삭제 → 회고 items 그대로 (스냅샷 회귀)
- 같은 회고에 동시 POST /items 두 건 → @Version 충돌 → 한쪽 재시도 후 성공, items.size = 2
- 같은 회고에 동시 POST /items 세 건 (재시도도 실패하는 케이스) → 최소 1개는 `RETROSPECTIVE_CONCURRENT_MODIFICATION` 응답
- 템플릿 동일 이름 두 번 POST → 두 번째 `RETROSPECTIVE_TEMPLATE_DUPLICATE`
- 다른 사용자의 모든 엔드포인트 → 404 (IDOR 회귀)
- AI 엔드포인트: 외부 AI API는 `MockRestServiceServer`로 stub, Rate Limit 11회 호출 → 11번째 `RATE_LIMITED` (테스트는 limit=10으로 빈 설정)

### 10.3 부하 (선택)
- 사용자당 회고 100개, 평균 items 20개 상태에서 카드별 회고 목록 GET p95 측정 (목표 ≤ 200ms)
- AI 엔드포인트는 외부 AI 서버 의존이라 본 도메인 부하 테스트 대상 아님

---

## 11. 마이그레이션

```
V8__create_retrospectives.sql
V9__create_retrospective_templates.sql
V10__index_retrospectives_application.sql
V11__index_retrospectives_user_stage.sql
V12__index_retrospective_templates_user.sql
V13__unique_retrospective_templates_user_name.sql
```

`retrospectives.application_id` FK + CASCADE는 `applications` 테이블이 먼저 생성된 이후(`application/tech.md` V4)에 적용. `users(id)` FK는 `auth/`의 V1 이후.

`items`/`questions` 컬럼은 `JSON` 타입 또는 `MEDIUMTEXT` (jackson 직렬화 문자열). MySQL 8 사용이므로 `JSON` 권장 — 내부 검색은 안 하지만 `JSON_VALID` 제약을 자동으로 받음.

---

## 12. 미해결/v2 후보

| 항목 | 현재 결정 | v2 검토 사유 |
|---|---|---|
| 항목 안정 ID | 인덱스 기반 | 동시 편집 빈번 / 항목 재정렬 UX 도입 시 UUID 발급. JSON 안에 `{ id, question, answer }` 구조로 확장 |
| 항목 재정렬 (drag) | 미지원 | 사용자 요청 시 `PUT /items` 전체 교체 또는 `PATCH /items/{id}/order` 추가 |
| `items` 컬럼 분리 | 단일 JSON | 항목당 메타(작성 시각, AI 생성 여부 태그)가 늘어나면 `retrospective_items` 테이블로 정규화 검토 |
| 템플릿 카테고리별 시드 | 없음 | "서류/코테/면접" StageCategory별 시스템 기본 템플릿 제공 |
| 템플릿 공유 / 마켓플레이스 | 없음 | 사용자 간 공유 요구 시 |
| AI 분석 (회고 → 약점 키워드) | 없음 | 회고 누적 후 통계 도메인 별도 분리 |
| `RetrospectiveQuestionGenerator`의 `Suggestion` 패턴화 | 동기 응답 | 비동기 / 사용자 알림 후 수락 흐름 도입 시 |
| 항목 점수·태그 등 구조화 필드 | `answer` 자유 텍스트 | "면접 난이도 1~5", 태그 등 추가 필드 도입 |
| Stage 삭제 표시 | 응답에 stageId만 | 조회 시 stage join + `stageDeleted: true` 표식 |

---

## 13. 구현 순서 제안

1. `global/persistence/`에 `RetrospectiveItemListConverter`, `StringListConverter` (또는 일반 `JsonListConverter<T>`) 추가
2. Flyway 마이그레이션 V8~V13 작성, Testcontainers로 적용 검증
3. `Retrospective` 엔티티 + `RetrospectiveItem` 값 객체 + Repository + Service (CRUD만, 항목/템플릿 제외) + Controller + 단위 테스트
4. 항목 단위 엔드포인트 (POST/PATCH/DELETE items) + `@Version` 낙관적 락 + 재시도 어드바이스
5. `RetrospectiveTemplate` 엔티티 + CRUD + UNIQUE 위반 핸들링
6. `apply-template` 엔드포인트 + 통합 테스트 (스냅샷 회귀)
7. `RetrospectiveQuestionGenerator` 인터페이스 정의 (`retrospective/ai/`) + `ExternalRetrospectiveQuestionGenerator`/`MockRetrospectiveQuestionGenerator` 구현
8. AI 엔드포인트 Controller/Service + Rate Limit + 외부 AI API 설정 연동
9. 통합 테스트(Testcontainers) — IDOR 회귀, CASCADE, 낙관적 락, 템플릿 스냅샷
10. CLAUDE.md의 "API 요약" 표 갱신, 본 tech.md §14 미구현 현황 갱신

---

## 14. 미구현 현황 (스냅샷: 2026-05-02)

본 도메인은 **신규 설계** 단계로 코드는 아직 한 줄도 들어가지 않았다. 모든 항목이 미구현이며, 표는 PR 단위로 진행 상황을 추적하기 위한 골격이다.

### 14.1 본 도메인 내부 — 즉시 처리 가능

| 항목 | 위치 | 상태 | 비고 |
|---|---|---|---|
| `Retrospective`, `RetrospectiveTemplate` 엔티티 | `domain/` | ❌ 미구현 | 현재 패키지에 `CLAUDE.md`/`tech.md`만 존재 |
| `RetrospectiveItem` 값 객체 | `domain/` | ❌ 미구현 | data class, JSON 직렬화 대상 |
| Repository 4개 메서드 (`findByIdAndUserId`, `findByApplicationIdAndUserId`, `findByUserIdAndName`, `existsByUserIdAndName`) | `repository/` | ❌ 미구현 | |
| Service 3개 (`RetrospectiveService`, `RetrospectiveTemplateService`, `RetrospectiveQuestionService`) | `service/` | ❌ 미구현 | §3 엔드포인트 1:1 매핑 |
| Controller 2개 + AI 엔드포인트 | `controller/` | ❌ 미구현 | |
| `@Version` 낙관적 락 + 재시도 어드바이스 | `service/RetrospectiveService.kt` | ❌ 미구현 | Spring Retry 또는 수동 try/catch 1회 |
| 항목 인덱스 검증 helper | `domain/Retrospective.kt` | ❌ 미구현 | `require(index in items.indices)` |
| 단위 테스트 (§10.1) | `src/test/kotlin/.../retrospective/` | ❌ 미구현 | |

### 14.2 다른 도메인/인프라 의존 — 해당 도메인 도입 시 본 도메인 코드 수정 필요

| 항목 | 상태 | 트리거 |
|---|---|---|
| JSON `AttributeConverter` 두 개 (`global/persistence/`) | ❌ 미구현 | 본 도메인 첫 PR에서 같이 도입. 다른 도메인의 JSON 컬럼이 늘면 일반 `JsonListConverter<T>`로 리팩토링 |
| `application/`의 `findByIdAndUserId` 노출 | ⚠️ 해당 도메인이 이미 가지고 있다고 가정 | `application/` Service의 inner API로 의존성 주입 |
| `stage/` (현재 `application/` 내부) lookup | ⚠️ 동일 | |
| `auth/` JWT + `@CurrentUser` | ❌ 다른 도메인과 공유 미구현 | 모든 Controller가 의존 |
| `global/ratelimit/` | ❌ 미구현 | AI 엔드포인트 도입 전 stub or 실 구현 필요 |
| 외부 AI API client 설정 (`base-url`, timeout, auth header) | ❌ 미구현 | `ExternalRetrospectiveQuestionGenerator` 도입 시 필요 |
| Flyway 마이그레이션 인프라 | ❌ 미구현 (`ddl-auto=update` 추정) | 마이그레이션 도입과 함께 V8~V13 적용 |

### 14.3 인프라 / 횡단 관심사

| 항목 | 상태 | 비고 |
|---|---|---|
| 통합 테스트 (Testcontainers) | ❌ 미구현 | §10.2 시나리오 미커버 |
| 부하 테스트 | ❌ 미구현 | 회고 100개 + items 20개 케이스 |
| 외부 AI API 비용/latency 모니터링 | ❌ 미구현 | AI 서버/인프라 책임. 본 도메인은 Rate Limit과 호출 로그만 |
| 응답 본문 XSS 방어 | ⚠️ 프론트 책임 | 서버는 길이 제한만 |

### 14.4 명시적 비범위 (이 도메인이 처리하지 않음)

- 회고 통계·약점 키워드 추출 — Phase 2 별도 도메인
- AI 메일 분류 → Suggestion — `ai/`, `mail/` 책임
- 카드/단계 자체의 CRUD — `application/` 책임 (본 도메인은 ID로만 참조)
- 외부 캘린더 export — `schedule/` 책임 (회고는 일정 아님)

---

## 15. 실제 AI 서버 연동 계획 (최신: 2026-06-02)

본 절은 회고 질문 생성 모델을 mock 데이터에서 실제 AI 서버로 전환하기 위한 구현 계획이다. 기존에는 같은 EC2 안에서 `http://127.0.0.1:8002`를 호출할 수 있다는 전제가 있었지만, 서버 분리 이후에는 **백엔드 EC2와 AI 서버가 서로 다른 서버**라는 전제로 설계한다. 따라서 `127.0.0.1`은 로컬 개발에서만 유효하며, dev/prod는 환경변수로 주입한 AI 서버의 private DNS/IP 또는 HTTPS URL을 사용한다.

### 15.1 목표 아키텍처

```
Frontend
  └─ POST /api/retrospectives/ai-questions  (JWT)
       ↓
Backend EC2
  ├─ userId 인증/인가
  ├─ Application/Stage 소유권 검증
  ├─ AI 서버 요청 DTO로 변환
  └─ POST {AI_BASE_URL}/ai/retrospective/questions  (internal auth)
       ↓
AI Server
  └─ 회고 질문 생성 응답
       ↓
Backend EC2
  ├─ 응답 스키마/길이 검증
  └─ ApiResponse로 프론트에 반환
```

프론트는 AI 서버를 직접 호출하지 않는다. 백엔드가 계속 인증, IDOR 방지, rate limit, 응답 정규화의 경계가 된다.

### 15.2 외부 AI 서버 계약

AI 서버 엔드포인트:

```http
POST /ai/retrospective/questions
```

AI 서버 요청:

```json
{
  "user_id": 1,
  "job_posting_title": "카카오 백엔드 개발자 채용",
  "company_name": "카카오",
  "job_role": "백엔드 개발자",
  "process_stage": "1차 기술면접",
  "question_count": 4
}
```

AI 서버 응답:

```json
{
  "question_set_title": "1차 기술면접 회고 질문",
  "job_role": "백엔드 개발자",
  "process_stage": "1차 기술면접",
  "questions": [
    {
      "category": "technical_depth",
      "question": "카카오 백엔드 기술면접에서 가장 답변이 부족했던 기술 개념은 무엇이었나요?",
      "reason": "기술 보완 포인트를 찾기 위함입니다.",
      "priority": "high",
      "source_template_ids": ["q_backend_interview_001"]
    }
  ]
}
```

백엔드 내부 DTO는 AI 서버 계약에 맞춰 snake_case를 명시한다.

```kotlin
data class AiRetrospectiveQuestionRequest(
    @JsonProperty("user_id")
    val userId: Long,
    @JsonProperty("job_posting_title")
    val jobPostingTitle: String,
    @JsonProperty("company_name")
    val companyName: String,
    @JsonProperty("job_role")
    val jobRole: String,
    @JsonProperty("process_stage")
    val processStage: String,
    @JsonProperty("question_count")
    val questionCount: Int,
)
```

### 15.3 백엔드 공개 API 계약 변경

현재 백엔드 공개 API는 다음 엔드포인트를 유지한다.

```http
POST /api/retrospectives/ai-questions
```

요청 DTO는 질문 수를 받을 수 있게 확장한다.

```json
{
  "applicationId": 101,
  "stageId": 5,
  "questionCount": 4
}
```

검증 규칙:
- `applicationId`: 필수, 양수, 요청자 소유.
- `stageId`: 옵션. 값이 있으면 요청자 소유. 없으면 `Application.stageId`의 현재 단계를 사용하고, 조회 실패 시 `"전체 전형"`으로 대체.
- `questionCount`: 옵션. 기본값 4, 허용 범위 1..15.

백엔드 응답은 AI 서버의 메타데이터를 보존하는 형태로 확장한다.

```json
{
  "data": {
    "questionSetTitle": "1차 기술면접 회고 질문",
    "jobRole": "백엔드 개발자",
    "processStage": "1차 기술면접",
    "questions": [
      {
        "category": "technical_depth",
        "question": "카카오 백엔드 기술면접에서 가장 답변이 부족했던 기술 개념은 무엇이었나요?",
        "reason": "기술 보완 포인트를 찾기 위함입니다.",
        "priority": "high",
        "sourceTemplateIds": ["q_backend_interview_001"]
      }
    ]
  },
  "meta": { "...": "..." }
}
```

기존 프론트가 문자열 배열만 필요하면 `questions[].question`만 사용한다. 단, 서버에서 메타데이터를 버리지 않는다.

### 15.4 필드 매핑

| AI 요청 필드 | 백엔드 소스 | 비고 |
|---|---|---|
| `user_id` | 인증된 `userId` | 프론트 입력값을 믿지 않음 |
| `job_posting_title` | `Application.jobPostingTitle` | 현재 `Application`에 필드가 없으므로 추가 필요. 기존 row는 `"${companyName} ${position} 채용"`으로 fallback |
| `company_name` | `Application.companyName` | 필수 |
| `job_role` | `Application.position` | 현재 도메인명은 position이지만 AI 계약에는 job_role로 전달 |
| `process_stage` | `Stage.name` | 요청 `stageId` 우선, 없으면 `Application.stageId`, 그래도 없으면 `"전체 전형"` |
| `question_count` | `RetrospectiveQuestionRequest.questionCount ?: 4` | 1..15 검증 |

`jobPostingTitle`은 실제 공고명 품질을 위해 추가하는 것이 권장된다.

추가 대상:
- `Application` 엔티티 nullable 컬럼 `job_posting_title`
- `CreateApplicationRequest`, `UpdateApplicationRequest`, `ApplicationResponse`, `BoardResponse`
- 기존 데이터 fallback 로직

### 15.5 구현 컴포넌트

새 구성:

```
retrospective/
├── ai/
│   ├── RetrospectiveQuestionGenerator.kt          # 기존 interface 유지
│   ├── ExternalRetrospectiveQuestionGenerator.kt  # 실제 AI 서버 HTTP 호출
│   ├── MockRetrospectiveQuestionGenerator.kt      # local/test fallback
│   ├── RetrospectiveAiClientProperties.kt
│   └── RetrospectiveAiDtos.kt
└── service/
    └── RetrospectiveQuestionService.kt            # 소유권 검증 + rate limit + generator 호출
```

HTTP 클라이언트는 `spring-boot-starter-webmvc`에 포함된 `RestClient`를 우선 사용한다. WebFlux 의존성을 추가해야 하는 `WebClient`는 v1에서 쓰지 않는다.

Bean 선택:

```kotlin
@ConditionalOnProperty(
    prefix = "cheerup.ai.retrospective",
    name = ["mode"],
    havingValue = "external",
    matchIfMissing = true,
)
class ExternalRetrospectiveQuestionGenerator(...)

@ConditionalOnProperty(
    prefix = "cheerup.ai.retrospective",
    name = ["mode"],
    havingValue = "mock",
)
class MockRetrospectiveQuestionGenerator(...)
```

현재 `DefaultRetrospectiveQuestionGenerator`는 `MockRetrospectiveQuestionGenerator`로 이름을 바꾸고 local/test 전용으로 제한한다.

### 15.6 설정

공통 설정 키:

```yaml
cheerup:
  ai:
    retrospective:
      mode: external
      base-url: ${AI_RETROSPECTIVE_BASE_URL}
      question-path: /ai/retrospective/questions
      api-key: ${AI_INTERNAL_API_KEY:}
      connect-timeout: 1s
      read-timeout: 8s
      default-question-count: 4
      max-question-count: 15
```

프로파일별 원칙:
- `local`: 기본은 `mode: mock`. 실제 로컬 AI 서버를 띄운 경우 `AI_RETROSPECTIVE_BASE_URL=http://127.0.0.1:8002`로 override.
- `dev`: `AI_RETROSPECTIVE_BASE_URL` 필수. 가능하면 private DNS 또는 private IP 사용.
- `prod`: `AI_RETROSPECTIVE_BASE_URL`, `AI_INTERNAL_API_KEY` 필수. 누락 시 애플리케이션 부팅 실패가 맞다.

dev/prod에서 `127.0.0.1`을 기본값으로 두지 않는다. 서버가 분리된 상태에서 `127.0.0.1`은 백엔드 EC2 자신을 가리키므로 장애가 된다.

### 15.7 네트워크와 보안

- AI 서버가 같은 VPC/private subnet에 있으면 AI 서버 security group inbound는 백엔드 EC2 security group만 허용한다.
- 다른 VPC/외부망이면 HTTPS를 사용하고, 백엔드는 `X-Internal-Api-Key: {AI_INTERNAL_API_KEY}` 헤더를 붙인다.
- 프론트 JWT를 AI 서버에 전달하지 않는다. AI 서버는 백엔드 내부 인증 헤더만 검증한다.
- `X-Request-Id`를 AI 서버 호출에도 전달해 백엔드 로그와 AI 서버 로그를 연결한다.
- request/response 본문 전체를 info 로그로 남기지 않는다. 로그에는 `userId`, `applicationId`, latency, HTTP status, requestId 정도만 남긴다.
- 이 기능은 공고명/회사명/직무명/단계명만 전송한다. 메일 본문은 전송하지 않고 저장하지 않는다.

### 15.8 장애 처리 정책

| 상황 | 백엔드 처리 |
|---|---|
| DNS 실패, connection refused | `AI_GENERATION_FAILED` |
| connection timeout | `AI_GENERATION_TIMEOUT` |
| read timeout 8s 초과 | `AI_GENERATION_TIMEOUT` |
| AI 서버 4xx | 백엔드 계약/매핑 오류로 보고 `AI_GENERATION_FAILED` |
| AI 서버 5xx | `AI_GENERATION_FAILED` |
| AI 서버 429 | 사용자 rate limit과 혼동하지 않도록 v1은 `AI_GENERATION_FAILED`로 매핑 |
| JSON 파싱 실패 | `AI_GENERATION_FAILED` |
| `questions`가 비어 있음 | `AI_GENERATION_FAILED` |
| 질문이 1000자 초과 또는 blank | 해당 항목 제거. 제거 후 0개면 `AI_GENERATION_FAILED` |

자동 재시도는 v1에서 하지 않는다. 이 API는 사용자 동기 요청이고, AI 호출 비용이 있으므로 프론트의 명시적 재시도 버튼으로 처리한다.

Rate limit은 현재처럼 소유권 검증 이후, 외부 AI 호출 직전에 차감한다. 외부 호출 실패 시에도 차감 복구는 하지 않는다.

### 15.9 테스트 계획

단위 테스트:
- `RetrospectiveQuestionServiceTest`: `questionCount` 기본값/범위, `stageId` 생략 시 application stage fallback, rate limit 순서 검증.
- `ExternalRetrospectiveQuestionGeneratorTest`: AI 서버 요청 URL, header, snake_case body 매핑 검증.
- AI 서버 정상 응답을 camelCase 백엔드 응답으로 변환하는지 검증.
- 4xx/5xx/timeout/invalid JSON/empty questions를 각각 `RetrospectiveQuestionGenerationException` 또는 `RetrospectiveQuestionTimeoutException`으로 변환하는지 검증.

통합 성격 테스트:
- `MockRestServiceServer`로 외부 AI 서버를 대체해 controller → service → generator 전체 흐름 검증.
- sample payload를 고정 fixture로 두고 계약 회귀 테스트를 추가한다.
- `local/test` 프로파일에서 mock generator가 선택되고, `dev/prod` 프로파일에서 external generator가 선택되는지 검증한다.

수동 smoke test:

```bash
curl -X POST "$AI_RETROSPECTIVE_BASE_URL/ai/retrospective/questions" \
  -H "Content-Type: application/json" \
  -H "X-Internal-Api-Key: $AI_INTERNAL_API_KEY" \
  -d '{
    "user_id": 1,
    "job_posting_title": "카카오 백엔드 개발자 채용",
    "company_name": "카카오",
    "job_role": "백엔드 개발자",
    "process_stage": "1차 기술면접",
    "question_count": 4
  }'
```

### 15.10 배포 체크리스트

1. AI 서버팀과 `/ai/retrospective/questions` 요청/응답 스키마, timeout, 인증 헤더를 확정한다.
2. AI 서버가 dev/prod에서 접근 가능한 base URL을 제공한다. dev/prod에 `127.0.0.1`을 사용하지 않는다.
3. 백엔드 EC2 security group egress와 AI 서버 inbound를 연결한다.
4. `AI_RETROSPECTIVE_BASE_URL`, `AI_INTERNAL_API_KEY`를 dev/prod secret으로 등록한다.
5. 백엔드에 external generator와 properties를 추가하고, mock generator는 local/test 전용으로 제한한다.
6. `Application.jobPostingTitle` 추가 여부를 결정한다. 추가한다면 마이그레이션/DTO/응답까지 같이 반영한다.
7. `RetrospectiveQuestionRequest`에 `questionCount`를 추가하고 Swagger 문서를 갱신한다.
8. AI 응답 메타데이터를 보존하는 `RetrospectiveQuestionsResponse`로 확장한다.
9. 단위/통합 테스트를 추가한 뒤 `./gradlew test`를 통과시킨다.
10. dev에서 백엔드 API를 호출해 AI 서버까지 실제 왕복되는지 latency와 로그 requestId를 확인한다.

### 15.11 완료 기준

- `DefaultRetrospectiveQuestionGenerator` mock 질문이 dev/prod에서 더 이상 사용되지 않는다.
- 백엔드 `POST /api/retrospectives/ai-questions` 호출 시 별도 AI 서버의 실제 응답이 반환된다.
- AI 서버 장애 시 프론트가 기존 오류 코드(`AI_GENERATION_FAILED`, `AI_GENERATION_TIMEOUT`, `RATE_LIMITED`)로 분기할 수 있다.
- dev/prod 설정에서 `127.0.0.1:8002` 의존이 사라진다.
- 테스트에서 AI 서버 계약 payload와 응답 스키마가 고정 fixture로 검증된다.
