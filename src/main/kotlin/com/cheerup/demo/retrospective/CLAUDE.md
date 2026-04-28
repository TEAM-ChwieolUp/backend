# retrospective — 단계별/종합 회고

지원 카드에 대한 사용자 회고 기록. 단계별로 작성하거나 카드 전체에 대한 종합 회고로 작성 가능.

## 엔티티

### Retrospective

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK |
| `userId` | `Long` (val) | NOT NULL, INDEX, FK ON DELETE CASCADE |
| `applicationId` | `Long` (val) | NOT NULL, **FK → applications(id) ON DELETE CASCADE** |
| `stageId` | `Long?` (val) | nullable, INDEX (FK 안 검) |
| `summary` | `String?` (TEXT) | 한 줄 요약 |
| `customFields` | `Map<String, Any>` (JSON) | 사용자 자유 입력 |

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

## customFields

Phase 1은 자유 JSON. 사용자가 키를 직접 정함:

```json
{
  "잘한 점": "기술 면접에서 라이브 코딩 잘 풀었음",
  "아쉬운 점": "프로젝트 깊이 질문에서 막힘",
  "면접 난이도": 4,
  "다음에 시도할 것": "시스템 디자인 공부"
}
```

Phase 2에서 `RetrospectiveField` 엔티티로 사용자별 템플릿화 예정 — 그때까지는 프론트가 자주 쓰는 키를 추천 UI로 제공.

## 삭제 정책

| 트리거 | 결과 |
|---|---|
| `User` 삭제 | DB FK CASCADE로 자동 삭제 |
| `Application` 삭제 | **DB FK CASCADE로 자동 삭제** — 카드의 모든 회고 함께 정리 |
| `Stage` 삭제 | `Retrospective.stageId`에 FK 제약 안 검 → **회고는 보존**, `stageId` 값은 dangling |
| 사용자가 회고 직접 삭제 | hard delete |

> Stage 삭제 시 회고가 dangling `stageId`를 갖게 되는 점은 **의도된 설계**. 회고는 학습 자산이라 단계 컬럼이 사라져도 보존. 조회 시 `stageId`로 stage가 없으면 "삭제된 단계"로 표시.

## 도메인 메서드

```kotlin
@Entity
@Table(name = "retrospectives")
class Retrospective(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "application_id", nullable = false)
    val applicationId: Long,

    @Column(name = "stage_id")
    val stageId: Long?,

    @Column(columnDefinition = "TEXT")
    var summary: String?,

    @Convert(converter = JsonMapConverter::class)
    @Column(columnDefinition = "JSON")
    var customFields: Map<String, Any> = emptyMap(),
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun update(summary: String?, customFields: Map<String, Any>) {
        this.summary = summary
        this.customFields = customFields
    }
}
```

## 권장 인덱스

- `retrospectives(application_id)` — 카드별 회고 목록
- `retrospectives(user_id, stage_id)` — "면접 단계 회고 모아보기"

## API 요약

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/applications/{appId}/retrospectives` | 카드의 회고 목록 |
| POST | `/api/applications/{appId}/retrospectives` | 회고 작성 (`stageId` 옵션) |
| PATCH | `/api/retrospectives/{id}` | 회고 수정 |
| DELETE | `/api/retrospectives/{id}` | 회고 삭제 |

## Phase 2

| 항목 | 내용 |
|---|---|
| `RetrospectiveField` | 사용자별 회고 항목 템플릿 (이름·타입·표시 순서) |
| 카테고리별 템플릿 | `StageCategory`마다 다른 기본 템플릿 적용 |
| AI 분석 | "면접 회고에서 자주 나오는 약점 키워드" 추출 |
