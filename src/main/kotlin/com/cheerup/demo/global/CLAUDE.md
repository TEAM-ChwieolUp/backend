# global - 공통 인프라

도메인과 무관하게 모든 패키지가 공유하는 코드. 도메인 코드보다 먼저 만들고, 응답/예외/엔티티 공통 규칙을 여기서 고정한다.

## 하위 패키지

| 패키지 | 책임 |
|---|---|
| `base/` | `BaseEntity` |
| `config/` | JPA Auditing, Security, OpenAPI/Swagger 등 Spring 설정 |
| `config/swagger/` | Swagger 공통 응답 문서화 애너테이션/커스터마이저 |
| `exception/` | `ErrorCode`, `BusinessException`, `GlobalExceptionHandler` |
| `response/` | `ApiResponse<T>`, `Meta`, `ErrorResponse` |

## BaseEntity

모든 JPA 엔티티는 `BaseEntity`를 상속한다.

```kotlin
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: Instant
        protected set

    @LastModifiedDate
    @Column(nullable = false)
    lateinit var updatedAt: Instant
        protected set
}
```

- `deletedAt`은 두지 않는다. 현재 정책은 hard delete 기준이다.
- `config/`에서 `@EnableJpaAuditing`을 활성화한다.

## 성공 응답

성공 응답은 항상 `ApiResponse<T>`로 감싼다.

```json
{
  "data": {
    "id": 1
  },
  "meta": {
    "timestamp": "2026-04-27T12:00:00Z",
    "requestId": "01HW..."
  }
}
```

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
```

Controller 반환 기준:
- 일반 조회/수정: `ApiResponse<T>`
- 생성처럼 HTTP status를 명시해야 하는 경우: `ResponseEntity<ApiResponse<T>>`
- 삭제 성공: `204 No Content`를 기본값으로 한다.

Swagger 문서에서는 `ApiResponse<T>` 반환 타입을 기반으로 `{ data, meta }` 구조가 자동 생성된다. 별도의 `FooApiResponseDoc` 같은 문서 전용 wrapper DTO를 만들지 않는다.

## 실패 응답

실패 응답은 직접 정의한 `ErrorResponse`를 사용한다. 실패 응답은 성공 응답 wrapper로 감싸지 않는다.

```json
{
  "code": "STAGE_NOT_FOUND",
  "message": "칸반 카테고리를 찾을 수 없습니다.",
  "detail": "stageId=10",
  "timestamp": "2026-04-27T12:00:00Z",
  "path": "/api/stages/10"
}
```

Swagger 실패 응답은 도메인 `api/` 인터페이스 메서드의 `@SwaggerErrorResponses`에 선언한 `ErrorCode`를 기반으로 자동 생성된다. 같은 HTTP status의 여러 오류 코드는 하나의 응답 아래 examples로 묶인다.

```kotlin
@SwaggerErrorResponses(
    errors = [
        SwaggerErrorResponse(ErrorCode.UNAUTHORIZED),
        SwaggerErrorResponse(ErrorCode.USER_NOT_FOUND),
    ],
)
```

인증이 필요한 API는 해당 메서드에만 `@SecurityRequirement(name = "bearerAuth")`를 붙인다. 같은 도메인 안에 공개 API와 인증 API가 섞일 수 있으므로 인터페이스 전체에 붙이지 않는다.

```kotlin
data class ErrorResponse(
    val code: String,
    val message: String,
    val detail: String? = null,
    val timestamp: Instant = Instant.now(),
    val path: String? = null,
)
```

## ErrorCode

초기에는 `global.exception.ErrorCode` 단일 enum으로 관리한다. `HttpStatus`, 프론트가 분기할 `code`, 사용자에게 보여줄 수 있는 `message`를 한 곳에 묶는다.

```kotlin
enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청 한도를 초과했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    STAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "STAGE_NOT_FOUND", "칸반 카테고리를 찾을 수 없습니다."),
    STAGE_NOT_EMPTY(HttpStatus.CONFLICT, "STAGE_NOT_EMPTY", "카드가 있는 칸반 카테고리는 삭제할 수 없습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "지원 정보를 찾을 수 없습니다."),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "태그를 찾을 수 없습니다."),
    RETROSPECTIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "RETROSPECTIVE_NOT_FOUND", "회고를 찾을 수 없습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "일정을 찾을 수 없습니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다."),
}
```

도메인별 ErrorCode enum은 에러 코드가 50개 이상으로 커지고 도메인별 소유권이 명확해질 때 검토한다. 처음부터 나누면 공통 코드 중복, code 문자열 충돌, 프론트 문서화 비용이 커진다.

## BusinessException

도메인별 예외 클래스를 많이 만들지 않는다. 비즈니스 예외는 `BusinessException` 하나로 시작하고, 의미는 `ErrorCode`가 담당한다.

```kotlin
class BusinessException(
    val errorCode: ErrorCode,
    val detail: String? = null,
    cause: Throwable? = null,
) : RuntimeException(detail ?: errorCode.message, cause)
```

도메인 서비스 사용 예:

```kotlin
val stage = stageRepository.findByIdAndUserId(stageId, userId)
    ?: throw BusinessException(
        ErrorCode.STAGE_NOT_FOUND,
        detail = "stageId=$stageId",
    )
```

## GlobalExceptionHandler

`GlobalExceptionHandler`는 모든 예외를 `ResponseEntity<ErrorResponse>`로 변환한다.

| 예외 | 응답 |
|---|---|
| `BusinessException` | `errorCode.status` + `ErrorResponse` |
| `MethodArgumentNotValidException` | 400 `INVALID_INPUT` |
| `ConstraintViolationException` | 400 `INVALID_INPUT` |
| `HttpMessageNotReadableException` | 400 `INVALID_INPUT` |
| 그 외 `Exception` | 500 `INTERNAL_ERROR` |

예상하지 못한 예외는 서버 로그에만 상세 스택트레이스를 남기고, 클라이언트에는 `INTERNAL_ERROR` 메시지만 내려준다.

## 새 도메인 추가 체크리스트

1. 필요한 에러 코드를 `ErrorCode`에 `[DOMAIN]_[REASON]` 형태로 추가한다.
2. 도메인 `api/` 패키지에 Swagger 문서 인터페이스를 작성한다.
3. `api/` 인터페이스에는 `@Tag`, `@Operation`, `@SwaggerErrorResponses`, 필요한 메서드 단위 `@SecurityRequirement`만 둔다.
4. 엔티티는 `BaseEntity`를 상속한다.
5. 사용자 소유 리소스에는 `userId: Long` 컬럼과 인덱스를 둔다.
6. Repository에는 `findByIdAndUserId` 패턴을 둔다.
7. Service에서 소유권을 검증하고 `BusinessException`을 던진다.
8. Controller는 도메인 `api` 인터페이스를 구현하고, `ApiResponse<T>` 반환과 HTTP 매핑/실행 로직만 담당한다.

## 시간 처리

- 모든 시각은 UTC `Instant`로 저장한다.
- 프론트에서 사용자 타임존으로 변환한다.
- DB 컬럼 타입은 MySQL 8 기준 `DATETIME` 또는 `TIMESTAMP`를 사용한다.

## ID 생성 정책

- 모든 PK는 `@GeneratedValue(strategy = GenerationType.IDENTITY)` (`Long`)를 사용한다.
- 외부 노출 식별자가 필요한 엔티티에 한해 별도 `uuid` 컬럼을 검토한다.
