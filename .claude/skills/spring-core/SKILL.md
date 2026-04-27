---
name: spring-core
description: ChwieolUp(취얼업) 백엔드의 Kotlin + Spring Boot 4 + JPA + MySQL + Redis + Spring AI 코어 개발 컨벤션. 신규 도메인 추가, 엔티티/Repository/Service/Controller 작성, REST 엔드포인트 추가, JPA 매핑, 트랜잭션 처리, ApiResponse/ErrorCode 응답 표준화, AI Suggestion 패턴 구현, MockK 단위 테스트 또는 Testcontainers 통합 테스트 작성 시 반드시 사용한다. 사용자가 "도메인 추가", "엔드포인트 만들어줘", "엔티티 작성", "API 만들어줘", "회고/칸반/달력/메일 분류 기능 추가", "JPA Repository", "Service 계층", "Controller 작성", "테스트 작성"이라고 말하거나 src/main/kotlin 또는 src/test/kotlin 아래에 코드를 추가/수정하려는 모든 상황에서 트리거한다. 컨벤션을 모르고 작성하면 다른 도메인과 일관성이 깨지므로 반드시 본 스킬을 먼저 읽고 따라야 한다.
---

# spring-core — 취얼업 백엔드 코어 개발 가이드

이 스킬은 **취얼업(ChwieolUp)** 백엔드에서 일관된 도메인 코드를 만들기 위한 가이드다. CLAUDE.md의 설계 원칙을 코드 레벨로 구체화한 것이며, 신규 도메인/엔드포인트/테스트 작성 시 본 문서를 따른다.

대상 스택: **Kotlin 21 + Spring Boot 4.0.6 + Spring Data JPA + MySQL 8 + Redis + Spring AI**

---

## 0. 작업을 시작하기 전에

**먼저 확인할 것:**
1. `build.gradle.kts`에 Kotlin 플러그인(`kotlin("jvm")`, `kotlin("plugin.spring")`, `kotlin("plugin.jpa")`)과 `kotlin-reflect`, `jackson-module-kotlin`이 추가되어 있는가? 없다면 먼저 추가한다 — `references/build-gradle-kotlin.md` 참고.
2. 작업 대상 도메인이 `src/main/kotlin/com/cheerup/demo/<domain>/` 아래에 있는가? 없다면 신규 도메인 패키지부터 만든다.
3. 사용자 컨텍스트(`@CurrentUser`), 표준 응답(`ApiResponse`), 예외(`ErrorCode`, `BusinessException`)가 `global/` 아래에 이미 있는가? **없다면 먼저 만든다 — 도메인 코드보다 인프라가 우선이다.** `references/global-infra.md` 참고.

**왜 중요한가**: 도메인 코드를 먼저 짜고 나중에 ApiResponse/ErrorCode 같은 공통 인프라를 끼워 맞추려 하면 도메인마다 응답 형식이 어긋나고, IDOR 검증 누락 같은 보안 결함이 생긴다. 인프라가 컨벤션을 강제하는 구조여야 한다.

---

## 1. 도메인 패키지 구조 (필수)

모든 도메인은 동일한 6개 하위 패키지로 분리한다. 이 구조를 깨면 다른 도메인과 일관성이 사라진다.

```
com/cheerup/demo/<domain>/
├── controller/    # REST 엔드포인트 (HTTP만 담당, 비즈니스 로직 금지)
├── service/       # 트랜잭션 경계 + 비즈니스 로직
├── repository/    # Spring Data JPA Repository
├── domain/        # @Entity 클래스 + enum
├── dto/           # Request/Response data class
└── exception/     # 도메인 전용 예외 (선택, 공통 ErrorCode로 충분하면 생략)
```

**왜 분리하나**: Controller가 두꺼워지면 테스트하기 어렵고, Service 안에 HTTP 응답 로직이 섞이면 재사용이 막힌다. 계층 분리는 단위 테스트 작성 비용을 결정한다.

---

## 2. JPA 엔티티 작성 패턴

### 기본 형태

```kotlin
@Entity
@Table(name = "applications")
class Application(
    @Column(nullable = false)
    var companyName: String,

    @Column(nullable = false)
    var position: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var stage: ApplicationStage,

    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun changeStage(newStage: ApplicationStage) {
        this.stage = newStage
    }
}
```

### 필수 규칙

- **`class`(open) 사용** — `kotlin-spring`/`kotlin-jpa` 플러그인이 no-arg 생성자와 open class를 자동 처리한다. `data class`로 엔티티를 만들지 말 것 (equals/hashCode가 PK로 안 잡혀 컬렉션 동작이 깨진다).
- **소유자 식별 필드(`userId`)는 `val`** — 한 번 정해지면 바뀌지 않는다.
- **변경 가능 필드는 `var`** — 그러나 외부에서 직접 set하지 말고 의도가 드러나는 도메인 메서드(`changeStage`, `addTag`, `accept` 등)를 만들어 호출한다. setter 노출은 무결성 검증을 우회시킨다.
- **모든 엔티티는 `BaseEntity` 상속** — `createdAt`, `updatedAt`, `deletedAt` 자동 관리. Soft delete 정책 통일.
- **`@Enumerated(EnumType.STRING)`** — ORDINAL은 enum 순서 변경 시 데이터가 깨지므로 절대 사용 금지.
- **테이블/컬럼명은 `snake_case`**, Kotlin 필드는 `camelCase`. JPA가 매핑한다.

### 컬렉션과 연관관계

- **즉시 로딩(EAGER) 금지** — 항상 `LAZY`. N+1은 fetch join이나 `@EntityGraph`로 해결.
- 양방향 연관관계는 가급적 피하고, 필요하면 편의 메서드(`addRetrospective`)로 양쪽을 함께 관리.

### 커스텀 필드(JSON)

사용자 정의 항목은 `@Convert`로 JSON ↔ `Map<String, Any>` 변환:

```kotlin
@Convert(converter = JsonMapConverter::class)
@Column(name = "custom_fields", columnDefinition = "JSON")
var customFields: Map<String, Any> = emptyMap()
```

---

## 3. Repository 작성

```kotlin
interface ApplicationRepository : JpaRepository<Application, Long> {

    fun findByIdAndUserId(id: Long, userId: Long): Application?

    @Query("""
        select a from Application a
        where a.userId = :userId
          and (:stage is null or a.stage = :stage)
    """)
    fun findAllForBoard(userId: Long, stage: ApplicationStage?): List<Application>
}
```

### 규칙

- **모든 조회 메서드에 `userId` 조건 포함** — IDOR 방지. `findById(id)`만 호출해 다른 사용자 리소스가 새는 사고를 막는다. PK 단건 조회도 `findByIdAndUserId(id, userId)`로 작성.
- **Native Query는 최후의 수단** — JPQL로 표현할 수 없는 윈도우 함수, CTE, JSON 함수 등에서만 사용.
- **반환 타입은 `?` 또는 `Optional` 중 하나로 일관** — 본 프로젝트는 Kotlin nullable(`?`)을 기본으로 한다.

---

## 4. Service 작성

```kotlin
@Service
@Transactional(readOnly = true)
class ApplicationService(
    private val applicationRepository: ApplicationRepository,
) {

    fun getBoard(userId: Long, stage: ApplicationStage?): List<ApplicationResponse> =
        applicationRepository.findAllForBoard(userId, stage)
            .map { it.toResponse() }

    @Transactional
    fun create(userId: Long, request: ApplicationCreateRequest): ApplicationResponse {
        val application = Application(
            companyName = request.companyName,
            position = request.position,
            stage = request.stage,
            userId = userId,
        )
        return applicationRepository.save(application).toResponse()
    }

    @Transactional
    fun changeStage(userId: Long, id: Long, newStage: ApplicationStage): ApplicationResponse {
        val application = applicationRepository.findByIdAndUserId(id, userId)
            ?: throw BusinessException(ErrorCode.APPLICATION_NOT_FOUND)
        application.changeStage(newStage)
        return application.toResponse()
    }
}
```

### 규칙

- **클래스 레벨 `@Transactional(readOnly = true)`, 변경 메서드만 `@Transactional` 재선언** — 읽기 전용 트랜잭션은 dirty checking을 건너뛰어 약간 더 빠르고, 실수로 변경이 일어나면 감지된다.
- **생성자 주입(`val`) 단일 패턴** — `@Autowired` 필드 주입 금지. 테스트에서 mock 주입이 어려워진다.
- **소유자 검증은 항상 Service에서** — Controller에서 검증하면 다른 호출 경로(스케줄러, 메시지 핸들러)에서 누락된다.
- **DTO ↔ Entity 변환은 확장 함수로** — `Application.toResponse()`처럼 도메인 패키지 안에서 정의. Service 본문이 깔끔해진다.

---

## 5. Controller 작성

```kotlin
@RestController
@RequestMapping("/api/applications")
class ApplicationController(
    private val applicationService: ApplicationService,
) {

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun getBoard(
        @CurrentUser userId: Long,
        @RequestParam(required = false) stage: ApplicationStage?,
    ): ApiResponse<List<ApplicationResponse>> =
        ApiResponse.success(applicationService.getBoard(userId, stage))

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    fun create(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: ApplicationCreateRequest,
    ): ApiResponse<ApplicationResponse> =
        ApiResponse.success(applicationService.create(userId, request))

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun changeStage(
        @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: StageChangeRequest,
    ): ApiResponse<ApplicationResponse> =
        ApiResponse.success(applicationService.changeStage(userId, id, request.stage))
}
```

### 규칙

- **모든 인증 필요 엔드포인트에 `@PreAuthorize` + `@CurrentUser` 동시 적용** — 둘 중 하나만 있으면 보안 사고. PreAuthorize는 인증 강제, CurrentUser는 userId 주입.
- **Controller는 Service 호출과 ApiResponse 래핑만** — 분기/변환/트랜잭션은 모두 Service.
- **Request DTO는 `@Valid` + Bean Validation 어노테이션** (`@NotBlank`, `@Size`, `@Future`). 유효성 실패는 `GlobalExceptionHandler`가 `INVALID_INPUT`으로 변환.
- **응답은 항상 `ApiResponse<T>`** — 도메인 객체나 raw Map 반환 금지.

---

## 6. DTO 작성

```kotlin
data class ApplicationCreateRequest(
    @field:NotBlank
    val companyName: String,

    @field:NotBlank
    val position: String,

    val stage: ApplicationStage = ApplicationStage.APPLIED,

    val tags: List<String> = emptyList(),
)

data class ApplicationResponse(
    val id: Long,
    val companyName: String,
    val position: String,
    val stage: ApplicationStage,
    val createdAt: Instant,
)

fun Application.toResponse() = ApplicationResponse(
    id = id ?: error("Application is not persisted"),
    companyName = companyName,
    position = position,
    stage = stage,
    createdAt = createdAt,
)
```

### 규칙

- **`data class` + 모든 필드 `val`** — 불변. DTO를 변경할 일은 없다.
- **Bean Validation 어노테이션은 `@field:` 접두사** — Kotlin은 어노테이션이 property/field/getter 어디로 갈지 모호하므로 명시.
- **응답 DTO에 엔티티를 그대로 노출하지 말 것** — Lazy 로딩 컬렉션, 비밀 필드 노출, 순환 참조 직렬화 사고가 발생.

---

## 7. AI Suggestion 패턴 (메일 분류·일정 추출)

**핵심 원칙: AI는 절대 도메인 데이터를 직접 변경하지 않는다.** 항상 `Suggestion` 엔티티로 저장하고, 사용자가 명시적으로 수락한 경우에만 적용.

```kotlin
sealed interface SuggestionPayload {
    data class StageChange(val applicationId: Long, val newStage: ApplicationStage) : SuggestionPayload
    data class CreateEvent(val title: String, val startAt: Instant) : SuggestionPayload
}

@Service
class MailSuggestionService(
    private val aiClassifier: MailClassifier,
    private val suggestionRepository: SuggestionRepository,
    private val applicationService: ApplicationService,
    private val scheduleService: ScheduleService,
) {
    @Transactional
    fun analyzeAndPropose(userId: Long, mail: IncomingMail) {
        val payloads = aiClassifier.classify(mail)  // 메일 본문은 여기서만 사용, 저장 안 함
        payloads.forEach { suggestionRepository.save(Suggestion(userId, it)) }
    }

    @Transactional
    fun accept(userId: Long, suggestionId: Long) {
        val suggestion = suggestionRepository.findByIdAndUserId(suggestionId, userId)
            ?: throw BusinessException(ErrorCode.SUGGESTION_NOT_FOUND)
        when (val p = suggestion.payload) {
            is StageChange -> applicationService.changeStage(userId, p.applicationId, p.newStage)
            is CreateEvent -> scheduleService.create(userId, p.title, p.startAt)
        }
        suggestion.markAccepted()
    }
}
```

- **`sealed interface`로 payload 분기** — `when`이 모든 케이스를 강제 검사하므로 새 타입 추가 시 컴파일 에러로 알려준다.
- **메일 본문은 `aiClassifier.classify(mail)` 안에서만 존재** — 외부로 새지 않게 메서드 시그니처와 반환 타입에서 차단. `Suggestion`/엔티티/로그 어디에도 본문을 저장하지 말 것.

---

## 8. 예외와 응답 표준

`global/exception/ErrorCode.kt`:

```kotlin
enum class ErrorCode(val status: HttpStatus, val code: String, val message: String) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "요청 파라미터가 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다"),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "지원 정보를 찾을 수 없습니다"),
    SUGGESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "SUGGESTION_NOT_FOUND", "제안을 찾을 수 없습니다"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청 한도를 초과했습니다"),
}

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
```

- 도메인별 코드는 enum에 추가. 코드 문자열은 `[DOMAIN]_[REASON]` 형태로 기계 친화적이고 읽기 쉽게.
- 응답은 RFC 7807(Problem Details) 형식으로 `GlobalExceptionHandler`가 변환.

---

## 9. 테스트 작성 (필수)

### 단위 테스트 — JUnit 5 + MockK

```kotlin
class ApplicationServiceTest {
    private val applicationRepository = mockk<ApplicationRepository>()
    private val service = ApplicationService(applicationRepository)

    @Test
    fun `다른 사용자의 지원을 수정하려 하면 NOT_FOUND 예외`() {
        every { applicationRepository.findByIdAndUserId(1L, 99L) } returns null

        val ex = assertThrows<BusinessException> {
            service.changeStage(userId = 99L, id = 1L, newStage = ApplicationStage.INTERVIEW)
        }
        assertThat(ex.errorCode).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND)
    }
}
```

- **MockK를 사용** (Mockito 아님). Kotlin과 더 잘 어울리고 final 클래스 mock이 자연스럽다.
- **테스트 이름은 한국어 backtick** — 의도가 명확해진다.
- **소유자 검증, 단계 전이, 시간 계산 같은 도메인 규칙을 우선 테스트** — getter/setter 테스트는 가치 없음.

### 통합 테스트 — Testcontainers (MySQL 8 + Redis)

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
abstract class IntegrationTestBase {
    companion object {
        @Container @JvmStatic
        val mysql = MySQLContainer("mysql:8.0")

        @Container @JvmStatic
        val redis = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        @DynamicPropertySource @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}
```

- 각 테스트 후 모든 테이블 TRUNCATE + Redis FLUSHALL — 테스트 순서에 따른 깨짐 방지.
- Controller부터 DB까지 한번에 검증하는 시나리오 테스트는 통합 테스트로, 분기 폭발은 단위 테스트로 분리.

---

## 10. 새 도메인 추가 체크리스트

순서대로:

1. `src/main/kotlin/com/cheerup/demo/<domain>/` 아래 6개 하위 패키지 생성
2. `domain/` 엔티티 작성 (`BaseEntity` 상속, `class`+`var`, EAGER 금지)
3. `repository/` 작성 (모든 메서드에 `userId` 조건 포함)
4. `dto/` 작성 (`data class`+`val`, `@field:` 검증)
5. `service/` 작성 (`@Transactional(readOnly = true)` 클래스 + 변경 메서드 재선언)
6. `controller/` 작성 (`@PreAuthorize` + `@CurrentUser`, `ApiResponse` 래핑)
7. 신규 에러는 `global/exception/ErrorCode`에 추가
8. 단위 테스트(MockK) — 소유자 검증·도메인 규칙 우선
9. 통합 테스트(Testcontainers) — 핵심 사용자 플로우
10. CLAUDE.md의 "도메인 요약"·"엔드포인트" 표 갱신

---

## 11. 추가 참고

상세 패턴이 필요하면 다음 파일을 읽는다:

- `references/build-gradle-kotlin.md` — Gradle Kotlin 플러그인/의존성 설정
- `references/global-infra.md` — `BaseEntity`, `ApiResponse`, `@CurrentUser`, `GlobalExceptionHandler` 구현 가이드
