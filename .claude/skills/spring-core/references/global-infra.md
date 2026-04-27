# global/ 공통 인프라 구현 가이드

도메인 코드를 작성하기 전에 `src/main/kotlin/com/cheerup/demo/global/`에 다음 인프라가 갖춰져 있어야 한다. 한 번만 만들면 모든 도메인이 같은 컨벤션을 자동으로 따른다.

---

## 1. BaseEntity (`global/base/BaseEntity.kt`)

모든 엔티티의 공통 필드. JPA Auditing으로 자동 채워진다.

```kotlin
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {

    @CreatedDate
    @Column(updatable = false, nullable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    lateinit var updatedAt: Instant
        protected set

    @Column
    var deletedAt: Instant? = null
        protected set

    fun softDelete() {
        deletedAt = Instant.now()
    }

    val isDeleted: Boolean get() = deletedAt != null
}
```

`DemoApplication`에 `@EnableJpaAuditing` 추가 필요.

---

## 2. ApiResponse (`global/response/ApiResponse.kt`)

모든 응답의 표준 형식. Controller는 `data`만 신경 쓰고 `meta`는 자동.

```kotlin
data class ApiResponse<T>(
    val data: T?,
    val meta: Meta = Meta(),
) {
    companion object {
        fun <T> success(data: T): ApiResponse<T> = ApiResponse(data)
        fun empty(): ApiResponse<Unit> = ApiResponse(Unit)
    }
}

data class Meta(
    val timestamp: Instant = Instant.now(),
    val requestId: String = MDC.get("requestId") ?: UUID.randomUUID().toString(),
)
```

`requestId`는 요청별 추적용 — 인터셉터/필터에서 MDC에 넣어두면 로그·응답이 같은 ID로 묶인다.

---

## 3. ErrorCode + BusinessException (`global/exception/`)

```kotlin
enum class ErrorCode(val status: HttpStatus, val code: String, val message: String) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "요청 파라미터가 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청 한도를 초과했습니다"),
    // 도메인별로 enum 항목을 추가
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "지원 정보를 찾을 수 없습니다"),
    SUGGESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "SUGGESTION_NOT_FOUND", "제안을 찾을 수 없습니다"),
    SCHEDULE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_EVENT_NOT_FOUND", "일정을 찾을 수 없습니다"),
}

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
```

---

## 4. GlobalExceptionHandler (`global/exception/GlobalExceptionHandler.kt`)

RFC 7807 Problem Details 형식으로 변환.

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(ex.errorCode.status, ex.message ?: "")
        pd.setProperty("code", ex.errorCode.code)
        pd.setProperty("requestId", MDC.get("requestId"))
        return ResponseEntity.status(ex.errorCode.status).body(pd)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 파라미터가 올바르지 않습니다")
        pd.setProperty("code", ErrorCode.INVALID_INPUT.code)
        pd.setProperty("violations", ex.bindingResult.fieldErrors.map { mapOf("field" to it.field, "reason" to it.defaultMessage) })
        return ResponseEntity.badRequest().body(pd)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(): ResponseEntity<ProblemDetail> = error(ErrorCode.FORBIDDEN)

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ResponseEntity<ProblemDetail> {
        // 운영 환경에서는 ex 메시지를 노출하지 말 것 — 로그로만
        log.error("Unhandled exception", ex)
        return error(ErrorCode.INVALID_INPUT.let {
            ErrorCode.valueOf("INVALID_INPUT") // placeholder; 별도 INTERNAL_ERROR 코드 추가 권장
        })
    }
}
```

---

## 5. @CurrentUser (`global/aop/CurrentUser.kt`)

`HandlerMethodArgumentResolver`로 JWT 인증 정보에서 `userId`를 추출.

```kotlin
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUser

@Component
class CurrentUserResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(p: MethodParameter) =
        p.hasParameterAnnotation(CurrentUser::class.java) && p.parameterType == Long::class.java

    override fun resolveArgument(p: MethodParameter, mc: ModelAndViewContainer?, req: NativeWebRequest, bf: WebDataBinderFactory?): Long {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        return (auth.principal as? JwtPrincipal)?.userId
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
    }
}

@Configuration
class WebMvcConfig(private val currentUserResolver: CurrentUserResolver) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(currentUserResolver)
    }
}
```

`JwtPrincipal`은 `JwtAuthenticationFilter`가 토큰 파싱 후 `Authentication.principal`에 넣어두는 객체.

---

## 6. 보안 설정 (`global/config/SecurityConfig.kt`)

핵심만 발췌:

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // @PreAuthorize 활성화
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity, jwtFilter: JwtAuthenticationFilter): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/**").permitAll()
                  .anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
```

---

## 체크리스트

도메인 코드를 짜기 전에 위 6개 파일이 존재하는지 확인. 없으면 먼저 만들고 다음 PR에서 도메인 추가.
