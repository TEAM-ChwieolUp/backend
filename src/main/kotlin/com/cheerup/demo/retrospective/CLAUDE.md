# retrospective — 단계별/종합 회고 + 사용자 질문 템플릿

지원 카드에 대한 사용자 회고 기록. 회고는 **질문-답변 쌍의 목록**으로 구성되며, 사용자가 자주 쓰는 질문 묶음은 **RetrospectiveTemplate** 으로 저장해뒀다가 다시 가져올 수 있다. AI 모델에 회사/전형 정보를 넘겨 질문 리스트를 자동 생성하는 보조 기능도 제공한다.

회고는 단계별로 작성하거나 카드 전체에 대한 종합 회고로 작성할 수 있다.

## 엔티티

### Retrospective

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK |
| `userId` | `Long` (val) | NOT NULL, INDEX, FK ON DELETE CASCADE |
| `applicationId` | `Long` (val) | NOT NULL, **FK → applications(id) ON DELETE CASCADE** |
| `stageId` | `Long?` (val) | nullable, INDEX (FK 안 검) |
| `items` | `List<RetrospectiveItem>` (JSON) | 질문-답변 쌍 목록, 기본 `emptyList()` |

### RetrospectiveItem (값 객체, JSON에 직렬화)

| 필드 | 타입 | 제약 |
|---|---|---|
| `question` | `String` | NOT NULL, blank 금지 |
| `answer` | `String?` | nullable — 질문만 먼저 추가하고 답변은 나중에 채울 수 있음 |

순서는 리스트 순서로 보존. 식별자가 필요한 개별 조작은 인덱스 기반.

### RetrospectiveTemplate

사용자가 자주 쓰는 질문 묶음을 저장해 두고 회고에 바로 적용하기 위한 템플릿.

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK |
| `userId` | `Long` (val) | NOT NULL, INDEX, FK ON DELETE CASCADE |
| `name` | `String` | NOT NULL, 사용자 단위 unique 권장 |
| `questions` | `List<String>` (JSON) | 질문만 저장. 적용 시 답변은 `null` 로 채워져 회고 `items` 에 들어감 |

> 템플릿은 답변이 없는 "질문 목록"이다. 답변은 회고를 작성하면서 채우는 것이지 템플릿에 박아넣지 않는다.

## stageId nullable의 의미

| `stageId` | 의미 |
|---|---|
| `null` | **카드 종합 회고** — "이 회사 지원 전체에 대한 정리" |
| 값 있음 | **단계별 회고** — "1차 면접 끝난 시점의 회고" |

`stageId`는 **`val` (불변)**. 회고 작성 시점의 단계를 스냅샷으로 보존.
이후 `Application.stageId`가 바뀌어도 과거 회고들은 자기가 가리키던 단계를 유지.

```
시나리오:
1. 카카오 카드 stageId = 5 (1차 면접) 상태
2. "1차 면접 회고" 작성 → Retrospective.stageId = 5
3. 카드 stageId = 6 (2차 면접)으로 변경
4. "2차 면접 회고" 작성 → Retrospective.stageId = 6

→ 회고 1은 여전히 stageId=5를 가리킴 (스냅샷)
```

## 회고 작성 흐름

1. **빈 회고 생성** — `POST /api/applications/{appId}/retrospectives` 호출 시 `items` 는 빈 리스트로 시작.
2. **항목 추가** — 사용자가 질문 1개를 입력해 `POST /api/retrospectives/{id}/items` 로 한 쌍씩 append. 답변은 비워두고 질문만 추가하는 것도 허용.
3. **항목 수정/삭제** — 인덱스 기반 `PATCH`/`DELETE`. 답변만 채우는 흐름이 일반적.

대안 흐름:
- **템플릿 적용**: 빈 회고를 만든 직후(또는 도중에) `POST /api/retrospectives/{id}/apply-template` 로 템플릿의 질문들을 한 번에 `items` 로 주입 (기존 항목 뒤에 append).
- **AI 질문 생성**: `POST /api/retrospectives/ai-questions` 로 회사/전형 정보 기반 질문 목록을 받아오고, 프론트에서 사용자가 편집 후 항목으로 추가.

## 템플릿 적용

사용자는 회고 편집 화면에서 **자기가 만든 템플릿 전체 목록**을 보고, 하나를 골라 즉시 현재 회고에 적용한다. 두 단계 흐름:

```
GET  /api/retrospective-templates             ← 사용자 템플릿 전체 목록 (questions 포함)
       사용자가 1개 선택
POST /api/retrospectives/{id}/apply-template  body: { templateId }
       → 선택한 템플릿의 questions 를 회고 items 끝에 append (답변은 null)
```

- **스냅샷 복사**: 적용 시점에 템플릿의 `questions` 를 그대로 회고 `items` 로 복사. 이후 템플릿이 수정·삭제돼도 이미 적용된 회고에는 영향 없음.
- **누적 append**: 여러 템플릿을 연달아 적용하면 항목이 쌓인다 (덮어쓰기 아님).
- 적용 후에는 일반 항목 조작(`PATCH`/`DELETE /items/{index}`)으로 자유롭게 다듬는다.

## AI 질문 생성

`POST /api/retrospectives/ai-questions` — body `{ applicationId, stageId? }`
- 서버는 `Application` 의 회사명·포지션·메모, 그리고 `stageId` 가 있으면 해당 `Stage` 의 카테고리(서류/코딩테스트/면접 등)를 컨텍스트로 LLM 프롬프트 구성.
- LLM 응답은 JSON 스키마(`{ questions: List<String> }`)로 검증.
- 응답: `{ questions: ["...", "...", ...] }`. **DB 미반영.**
- 프론트가 사용자에게 보여주고, 사용자가 선별·편집 후 `POST /retrospectives/{id}/items` 로 추가.

> 전역 원칙 "AI 결과는 DB 직접 반영 금지" 와 일관됨 — 이 엔드포인트는 단순 응답이며, 항목 추가는 사용자의 명시적 행위로만 일어난다. `Suggestion` 엔티티를 별도로 만들 필요는 없다 (메일 분류와 달리 사용자가 즉석에서 요청하는 동기 흐름).

LLM 프롬프트 가드레일:
- 회사명/포지션 등 사용자 입력은 system 프롬프트와 분리된 user 메시지로 전달.
- Rate Limit: 메일 AI와 별도로 회고 AI 50회/일/계정 권장 (Phase 1은 메일 AI 한도 100회/일에 합산해도 무방).
- GPT-4o-mini 기본 사용.

## 삭제 정책

| 트리거 | 결과 |
|---|---|
| `User` 삭제 | DB FK CASCADE로 회고·템플릿 모두 자동 삭제 |
| `Application` 삭제 | **DB FK CASCADE로 자동 삭제** — 카드의 모든 회고 함께 정리 |
| `Stage` 삭제 | `Retrospective.stageId`에 FK 제약 안 검 → **회고는 보존**, `stageId` 값은 dangling |
| 사용자가 회고 직접 삭제 | hard delete |
| 사용자가 템플릿 삭제 | hard delete. 이미 적용된 회고는 영향 없음 (스냅샷이므로) |

> Stage 삭제 시 회고가 dangling `stageId`를 갖게 되는 점은 **의도된 설계**. 회고는 학습 자산이라 단계 컬럼이 사라져도 보존. 조회 시 `stageId`로 stage가 없으면 "삭제된 단계"로 표시.

## 도메인 메서드

```kotlin
data class RetrospectiveItem(
    val question: String,
    val answer: String?,
)

@Entity
@Table(name = "retrospectives")
class Retrospective(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "application_id", nullable = false)
    val applicationId: Long,

    @Column(name = "stage_id")
    val stageId: Long?,

    @Convert(converter = RetrospectiveItemListConverter::class)
    @Column(columnDefinition = "JSON")
    var items: MutableList<RetrospectiveItem> = mutableListOf(),
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun addItem(item: RetrospectiveItem) {
        require(item.question.isNotBlank()) { "질문은 비어 있을 수 없다" }
        items.add(item)
    }

    fun updateItem(index: Int, item: RetrospectiveItem) {
        require(index in items.indices) { "잘못된 항목 인덱스: $index" }
        require(item.question.isNotBlank()) { "질문은 비어 있을 수 없다" }
        items[index] = item
    }

    fun removeItem(index: Int) {
        require(index in items.indices) { "잘못된 항목 인덱스: $index" }
        items.removeAt(index)
    }

    fun appendQuestions(questions: List<String>) {
        questions.filter { it.isNotBlank() }
            .forEach { items.add(RetrospectiveItem(question = it, answer = null)) }
    }
}

@Entity
@Table(
    name = "retrospective_templates",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "name"])],
)
class RetrospectiveTemplate(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    var name: String,

    @Convert(converter = StringListConverter::class)
    @Column(columnDefinition = "JSON")
    var questions: MutableList<String> = mutableListOf(),
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun update(name: String, questions: List<String>) {
        this.name = name
        this.questions = questions.filter { it.isNotBlank() }.toMutableList()
    }
}
```

> `RetrospectiveItemListConverter` / `StringListConverter` 는 `global/` 의 JSON 컨버터 패턴(예: 기존 `JsonMapConverter`)을 따라 추가한다.

## 권장 인덱스

- `retrospectives(application_id)` — 카드별 회고 목록
- `retrospectives(user_id, stage_id)` — "면접 단계 회고 모아보기"
- `retrospective_templates(user_id)` — 사용자 템플릿 목록
- `retrospective_templates(user_id, name)` UNIQUE — 동일 이름 중복 방지

## API 요약

### 회고

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/applications/{appId}/retrospectives` | 카드의 회고 목록 |
| GET | `/api/retrospectives/{id}` | 회고 단건 조회 (items 포함) |
| POST | `/api/applications/{appId}/retrospectives` | 빈 회고 생성 (`stageId` 옵션) |
| DELETE | `/api/retrospectives/{id}` | 회고 삭제 |

### 회고 항목 (Q&A 쌍)

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/retrospectives/{id}/items` | 항목 1건 append (`{ question, answer? }`) |
| PATCH | `/api/retrospectives/{id}/items/{index}` | 항목 수정 (질문/답변 둘 다 또는 일부) |
| DELETE | `/api/retrospectives/{id}/items/{index}` | 항목 삭제 |

> 인덱스는 현재 `items` 리스트의 0-base 위치. 동시 편집 충돌이 우려되면 클라이언트가 최근 조회한 길이를 `If-Match` 류 헤더로 보내는 식으로 보강 가능 (Phase 2).

### 템플릿

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/retrospective-templates` | 사용자 템플릿 목록 |
| GET | `/api/retrospective-templates/{id}` | 템플릿 단건 조회 |
| POST | `/api/retrospective-templates` | 템플릿 생성 (`{ name, questions }`) |
| PATCH | `/api/retrospective-templates/{id}` | 템플릿 수정 |
| DELETE | `/api/retrospective-templates/{id}` | 템플릿 삭제 |
| POST | `/api/retrospectives/{id}/apply-template` | 템플릿의 질문들을 회고 `items` 끝에 append (`{ templateId }`) |

### AI 질문 생성

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/retrospectives/ai-questions` | `{ applicationId, stageId? }` → `{ questions: [...] }`. DB 미반영 |

## 권한 / IDOR

- 모든 엔드포인트는 `@CurrentUser` + `@PreAuthorize("isAuthenticated()")`.
- `Retrospective`, `RetrospectiveTemplate` 모두 `userId` 와 요청자 일치 검증. 불일치 시 403.
- `apply-template` 은 회고 소유자와 템플릿 소유자가 모두 요청자여야 함.

## Phase 2

| 항목 | 내용 |
|---|---|
| 항목 안정 ID | `items` 항목별 UUID 부여 → 인덱스 의존 제거, 동시 편집 견고성 |
| 템플릿 공유 | 카테고리(서류/코테/면접)별 시스템 기본 템플릿 + 사용자 간 공유 |
| AI 분석 | "면접 회고에서 자주 나오는 약점 키워드" 추출, 회고 간 패턴 비교 |
| 항목 타입 확장 | `answer` 외에 점수(1~5), 태그 등 구조화 필드 — 현재는 자유 텍스트만 |
