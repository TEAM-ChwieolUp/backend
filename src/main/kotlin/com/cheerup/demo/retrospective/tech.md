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
- AI 질문 생성 — `Application`/`Stage` 컨텍스트를 LLM에 넘겨 질문 목록 동기 응답 (DB 미반영)

### Out of Scope (다른 도메인이 처리)
| 책임 | 위임 대상 |
|---|---|
| `Application` 소유권 검증의 원본 | `application/` (요청 들어온 `applicationId`의 userId 일치 확인은 본 도메인에서 직접 lookup) |
| `Stage` 라벨/카테고리 조회 | `stage_id` 스냅샷만 보존, 표시용 정보는 조회 시 `application/`을 join하거나 응답에서 ID만 노출 |
| 마감 알림·달력 표시 | `notification/`, `schedule/` (회고는 알림/달력과 무관) |
| AI 메일 분류 → Suggestion | `ai/`, `mail/` (`Suggestion` 패턴은 비동기·승인 흐름이라 별개. 회고 AI는 동기 보조) |
| LLM 호출 인프라 (`ChatClient`, 프롬프트 빌더, 출력 스키마 검증) | `ai/` (본 도메인은 `RetrospectiveQuestionGenerator` 인터페이스만 호출) |
| 사용자 컨텍스트 / JWT | `global/jwt`, `@CurrentUser` |

### 비기능 요구
- 회고/템플릿 CRUD: **p95 ≤ 200ms**
- AI 질문 생성: 동기 응답이지만 LLM 왕복으로 **p95 ≤ 6s** 허용 (UI는 로딩 표시). 타임아웃 8s 후 `AI_GENERATION_FAILED` 반환
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
├── ai/            # RetrospectiveQuestionGenerator (interface) + 기본 구현은 ai/ 도메인에 둠
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

#### `POST /api/retrospectives/ai-questions`
컨텍스트(회사·포지션·단계)를 LLM에 넘겨 질문 목록을 받아온다. **DB 미반영**.

**요청**
```json
{ "applicationId": 101, "stageId": 5 }
```

**검증**
- `applicationId` 필수, 요청자 소유
- `stageId` 옵션, 있으면 요청자 소유

**처리**
1. `Application` lookup — `companyName`, `position`, `memo` 추출
2. `stageId` 있으면 `Stage` lookup — `category`(IN_PROGRESS / PASSED / REJECTED)와 `name`
3. `RetrospectiveQuestionGenerator.generate(context)` 호출 — `ai/` 구현이 system prompt + user 메시지 분리, JSON 스키마 강제(Spring AI structured output / function calling)
4. 응답 검증 — 질문 개수 1..15개, 각 질문 NotBlank ≤ 1000자
5. 응답 그대로 클라이언트에 반환. **`Suggestion` 엔티티 저장 없음**

**응답 200**
```json
{
  "data": {
    "questions": [
      "면접에서 어려웠던 질문은 무엇이었나요?",
      "지원 동기를 어떻게 답변했나요?",
      "..."
    ]
  }
}
```

**오류**
- 4xx: 입력 검증 실패 (`INVALID_INPUT`, `APPLICATION_NOT_FOUND` 등)
- 429: Rate Limit 초과 — `RATE_LIMITED`
- 502: LLM 호출 실패 또는 응답 스키마 위반 — `AI_GENERATION_FAILED` (재시도 가능 표시)
- 504: LLM 타임아웃 (8s) — `AI_GENERATION_TIMEOUT`

**Rate Limit**
- 사용자별 **일 50회**. 메일 AI 100회/일과 별도 카운터. `global/`의 RateLimit 인프라(미구현 시 본 도메인 도입과 함께 stub) 사용.

**프롬프트 가드레일**
- `Application.companyName`, `position`, `memo` 등 사용자 입력은 user 메시지로만 전달, system 프롬프트와 분리
- LLM 출력은 항상 JSON 스키마(`{ questions: string[] }`) 검증을 통과해야 함. 미통과 시 1회 재호출 후 실패 처리
- Spring AI + GPT-4o-mini 사용 (`global/CLAUDE.md` 모델 정책)

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

```
POST /api/retrospectives/ai-questions  body: { applicationId: 101, stageId: 5 }
  ↓
Service.generateQuestions(userId=99, request)  (no @Transactional — 읽기만 + 외부 호출)
  ├─ rateLimiter.tryAcquire(userId, "retrospective-ai", limit=50/day) → RATE_LIMITED
  ├─ app   = appRepo.findByIdAndUserId(101, 99) → APPLICATION_NOT_FOUND
  ├─ stage = stageId?.let { stageRepo.findByIdAndUserId(it, 99) } → STAGE_NOT_FOUND
  ├─ ctx   = QuestionContext(app.companyName, app.position, app.memo, stage?.name, stage?.category)
  ├─ result = generator.generate(ctx)              ← LLM 호출 (8s 타임아웃)
  │     ├ 실패 → AI_GENERATION_FAILED (502)
  │     ├ 타임아웃 → AI_GENERATION_TIMEOUT (504)
  │     └ 스키마 위반 → 1회 재시도 → 실패 시 AI_GENERATION_FAILED
  └─ return { questions: result.questions.filter { it.isNotBlank() } }
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
- **AI 질문 생성은 트랜잭션 없음** — DB write 없음. `Application`/`Stage` 조회는 별도 readOnly 트랜잭션 또는 트랜잭션 밖 단일 SELECT. LLM 호출이 트랜잭션을 잡지 않게 주의 (장기 트랜잭션 방지).
- **Rate Limiter 갱신**은 LLM 호출 *전*에 카운터 +1, 호출 실패 시에도 차감하지 않음 (단순화). LLM 비용 보호가 목적이므로 호출되지 않은 케이스(검증 실패)도 카운트 안 하도록 주의 — 검증 통과 직후 한 번만 acquire.
- **JSON 컬럼 갱신은 전체 컬럼 재기록**이다 — `items` 변경 시 row 전체 UPDATE. v1 규모(items 평균 ≤ 30개, ≤ 수십 KB)에서는 무시.

---

## 8. 동시성 고려

| 시나리오 | 위험 | 대응 |
|---|---|---|
| **같은 회고에 두 탭에서 동시에 항목 append** | T1, T2가 같은 items 읽고 각자 append → save → 하나 유실 | **`@Version` 낙관적 락 + 1회 재시도**. 재시도도 실패하면 `RETROSPECTIVE_CONCURRENT_MODIFICATION` (409) — 클라이언트가 GET 후 다시 시도 |
| 인덱스 기반 PATCH/DELETE 도중 다른 탭에서 항목 추가/삭제 | 인덱스가 의도한 항목과 다른 항목을 가리킴 | `@Version` 충돌로 한 쪽이 거부됨. 인덱스 안정성은 보장 못 하므로 클라이언트는 응답의 새 `items`로 화면 갱신 |
| 같은 템플릿 이름을 두 요청이 동시에 POST | UNIQUE 위반 | DB UNIQUE에 의존. `DataIntegrityViolationException` → `BusinessException(RETROSPECTIVE_TEMPLATE_DUPLICATE)` 변환 |
| AI 호출 중 회고가 삭제됨 | AI 결과를 받아도 회고가 없음 | AI 응답은 회고와 무관(회고에 직접 쓰지 않음). 사용자가 받은 questions를 `POST /items`로 추가하려 하면 `RETROSPECTIVE_NOT_FOUND` |

**왜 낙관적 락인가** — 단일 사용자 도메인이라 충돌 자체가 드물다(두 탭 동시 편집 정도). 비관적 락은 LLM 호출이 끼면 너무 무겁다. 빠른 두 번 클릭 같은 흔한 케이스만 방어하면 충분.

---

## 9. 보안 체크리스트

- [ ] 모든 Repository 메서드가 `userId` 조건 포함
- [ ] 모든 Controller에 `@PreAuthorize("isAuthenticated()")` + `@CurrentUser`
- [ ] 다른 사용자 리소스 조회 시 항상 **404** (존재 노출 방지)
- [ ] AI 호출 전 `applicationId`/`stageId` 소유 검증 — 다른 사용자 정보가 LLM 프롬프트에 새지 않게
- [ ] LLM 프롬프트: 사용자 입력은 user 메시지로만 전달, system 프롬프트와 분리 (Prompt Injection 방어)
- [ ] LLM 응답은 JSON 스키마 검증 후 사용
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

**RetrospectiveQuestionServiceTest** (LLM mock)
- `generate`: Rate Limit 초과 → `RATE_LIMITED`
- `generate`: 다른 사용자 application → `APPLICATION_NOT_FOUND`
- `generate`: LLM이 잘못된 JSON 반환 → 1회 재호출 후 `AI_GENERATION_FAILED`
- `generate`: LLM 타임아웃 → `AI_GENERATION_TIMEOUT`
- `generate`: 정상 응답에서 blank/너무 긴 질문 필터링
- prompt 빌더: 사용자 입력이 user 메시지에 들어가고 system 프롬프트에 escaping 없이 보간되지 않는지 (Prompt Injection 방어)

### 10.2 통합 (Testcontainers — MySQL + Redis)

- 회고 생성 → 항목 5개 누적 → 단건 GET으로 items 5개 확인
- `Application` 삭제 → 회고도 DB CASCADE로 사라짐 (`retrospectives` count = 0)
- `Stage` 삭제 → 회고는 보존되고 `stageId`는 그대로 (dangling 허용)
- 템플릿 생성 → 회고에 apply → 템플릿 삭제 → 회고 items 그대로 (스냅샷 회귀)
- 같은 회고에 동시 POST /items 두 건 → @Version 충돌 → 한쪽 재시도 후 성공, items.size = 2
- 같은 회고에 동시 POST /items 세 건 (재시도도 실패하는 케이스) → 최소 1개는 `RETROSPECTIVE_CONCURRENT_MODIFICATION` 응답
- 템플릿 동일 이름 두 번 POST → 두 번째 `RETROSPECTIVE_TEMPLATE_DUPLICATE`
- 다른 사용자의 모든 엔드포인트 → 404 (IDOR 회귀)
- AI 엔드포인트: LLM은 mock으로 stub, Rate Limit 11회 호출 → 11번째 `RATE_LIMITED` (테스트는 limit=10으로 빈 설정)

### 10.3 부하 (선택)
- 사용자당 회고 100개, 평균 items 20개 상태에서 카드별 회고 목록 GET p95 측정 (목표 ≤ 200ms)
- AI 엔드포인트는 LLM 외부 의존이라 본 도메인 부하 테스트 대상 아님

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
7. `RetrospectiveQuestionGenerator` 인터페이스 정의 (`retrospective/ai/`) + `ai/` 도메인이 들어오기 전 stub 구현
8. AI 엔드포인트 Controller/Service + Rate Limit 연동
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
| `ai/` 도메인의 `ChatClient` / Spring AI 설정 | ❌ 미구현 | `RetrospectiveQuestionGenerator` 기본 구현이 호출 |
| Flyway 마이그레이션 인프라 | ❌ 미구현 (`ddl-auto=update` 추정) | 마이그레이션 도입과 함께 V8~V13 적용 |

### 14.3 인프라 / 횡단 관심사

| 항목 | 상태 | 비고 |
|---|---|---|
| 통합 테스트 (Testcontainers) | ❌ 미구현 | §10.2 시나리오 미커버 |
| 부하 테스트 | ❌ 미구현 | 회고 100개 + items 20개 케이스 |
| LLM 비용 모니터링 | ❌ 미구현 | `ai/` 도메인 책임. 본 도메인은 Rate Limit만 |
| 응답 본문 XSS 방어 | ⚠️ 프론트 책임 | 서버는 길이 제한만 |

### 14.4 명시적 비범위 (이 도메인이 처리하지 않음)

- 회고 통계·약점 키워드 추출 — Phase 2 별도 도메인
- AI 메일 분류 → Suggestion — `ai/`, `mail/` 책임
- 카드/단계 자체의 CRUD — `application/` 책임 (본 도메인은 ID로만 참조)
- 외부 캘린더 export — `schedule/` 책임 (회고는 일정 아님)
