# AGENTS.md

이 문서는 Codex를 포함한 작업 에이전트가 본 코드베이스에서 작업할 때 참고하는 온보딩 가이드다.

> **현재 상태**: 본 프로젝트는 초기 스캐폴딩 단계다. 현재 `build.gradle.kts`는 Java 21 기반으로 셋업되어 있으나, **실제 개발은 Kotlin으로 진행한다.** 도메인 코드 작성 전 Kotlin 플러그인(`kotlin("jvm")`, `kotlin("plugin.spring")`, `kotlin("plugin.jpa")`)과 `kotlin-reflect`, `jackson-module-kotlin` 의존성을 추가해야 한다. 본 문서는 제안서에 정의된 목표 설계를 기준으로 한 **개발 가이드라인**이다.

---

## WHY — 프로젝트 존재 이유

**ChwieolUp(취얼업)** 은 취업준비생이 수십 개 기업의 지원 현황·일정·회고를 한 곳에서 관리할 수 있게 해주는 **취준생 전용 올인원 취업 매니저**의 백엔드다.

핵심 가치:
- **풀사이클 통합 관리**: 노션/스프레드시트/원티드/Google Calendar로 흩어진 워크플로우(공고→지원→일정→회고)를 단일 도메인 모델로 통합
- **AI는 보조, 사용자는 최종 결정자**: 채용 메일 분류·일정 추출은 AI가 수행하지만, 결과는 항상 **Suggestion** 형태로 제시되며 사용자 수락 시에만 실제 데이터에 반영
- **메일 본문 비저장 원칙**: 채용 메일은 LLM 분석에만 사용하고 서버에 저장하지 않음 (분류 결과·추출된 일정만 보관)
- **유연한 사용자 정의**: 채용 전형은 사람마다 다르므로 단계·태그·회고 항목을 모두 커스터마이즈 가능하게 설계

---

## WHAT — 시스템이 하는 일

### 도메인 요약

| 도메인 | 역할 | 핵심 엔티티 (예정) |
|--------|------|-------------------|
| `application` | 채용 칸반 보드 — 지원 기업 카드, 단계 전이, 태그 | `Application`, `Stage`, `Tag` |
| `retrospective` | 단계별 회고 (커스텀 필드 포함) | `Retrospective`, `RetrospectiveField` |
| `schedule` | 취업 이중 달력 — 채용공고/지원 프로세스/개인의 3레이어 | `ScheduleEvent` |
| `notification` | 마감 D-3/D-1/당일 알림 스케줄링 (Redis Sorted Set) | (엔티티 없음, Redis 기반) |
| `mail` | Gmail/Outlook OAuth 연동, 메일 수신 Webhook | `MailIntegration` |
| `ai` | 채용 메일 분류, 일정 추출, 제안 생성 (Spring AI + GPT-4o-mini) | `Suggestion` |
| `auth` | OAuth2 로그인, JWT 발급/갱신 | `User`, `RefreshToken` |
| `export` | Google Calendar / Outlook 캘린더 내보내기 (iCalendar) | (엔티티 없음) |

### 전체 API 엔드포인트 (제안서 기준)

#### 칸반 보드 (`/api/applications`)
| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| GET | `/api/applications` | 칸반 보드 조회 (status/tag 필터) | USER |
| POST | `/api/applications` | 지원 현황 등록 | USER |
| PATCH | `/api/applications/{id}` | 단계/태그/메모 수정 (드래그앤드롭 단계 변경 포함) | USER |
| DELETE | `/api/applications/{id}` | 지원 삭제 | USER |
| POST | `/api/applications/{id}/retrospectives` | 회고 작성 (커스텀 필드 포함) | USER |

#### 취업 달력 (`/api/schedule`)
| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| GET | `/api/schedule/calendar` | 달력 데이터 조회 (레이어별 필터) | USER |
| POST | `/api/schedule/events` | 일정 직접 등록 | USER |
| POST | `/api/schedule/events/{id}/export` | Google/Outlook 캘린더로 내보내기 | USER |

#### 메일 AI 보조 (`/api/mail`, MVP 2)
| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| POST | `/api/auth/oauth/{provider}` | Gmail/Outlook OAuth 연결 | USER |
| GET | `/api/mail/suggestions` | AI 분석 결과 기반 업데이트 제안 목록 | USER |
| POST | `/api/mail/suggestions/{id}/accept` | 제안 수락 → 칸반/달력 자동 반영 | USER |

#### 인증 (`/api/auth`)
| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| POST | `/api/auth/login/{provider}` | 소셜 로그인 | 없음 |
| POST | `/api/auth/refresh` | Access/Refresh 토큰 갱신 | 없음 |

### API 공통 규칙

- **응답 형식**: `{ "data": {...}, "meta": { "timestamp": "...", "requestId": "..." } }`
- **오류 응답**: RFC 7807 Problem Details 표준
- **인증**: 모든 API는 JWT Bearer 토큰 필수 (`/api/auth/*` 제외)

### 오류 코드

| HTTP | 코드 | 설명 |
|------|------|------|
| 400 | `INVALID_INPUT` | 요청 파라미터 유효성 실패 |
| 401 | `UNAUTHORIZED` | JWT 토큰 없음/만료 — `/api/auth/refresh` 필요 |
| 403 | `FORBIDDEN` | 다른 사용자 리소스 접근 시도 (IDOR 방지) |
| 404 | `NOT_FOUND` | 리소스 미존재 |
| 429 | `RATE_LIMITED` | 로그인 5회/분, 메일 AI 100회/일 초과 |

### 스케줄러 (예정)

| 작업 | 주기 | 설명 |
|------|------|------|
| 마감 알림 발송 | 1분마다 | Redis Sorted Set의 `trigger_at` 폴링하여 D-3/D-1/당일 알림 발송 |
| 만료 토큰 정리 | 일 1회 | 만료된 RefreshToken 삭제 |

---

## HOW — 기술 구현

### 기술 스택

| 구분 | 기술 |
|------|------|
| 언어 | **Kotlin** (JVM 21 타겟) |
| 프레임워크 | Spring Boot 4.0.6 — `spring-boot-starter-data-jpa`, `spring-boot-starter-webmvc` |
| 데이터베이스 | MySQL 8 + Redis (알림 스케줄링 / 세션 캐시) |
| AI/LLM | Spring AI + OpenAI GPT-4o-mini (Function Calling 기반 구조화 추출) |
| 빌드 | Gradle (Kotlin DSL — `build.gradle.kts`) |
| Kotlin 플러그인 | `kotlin("jvm")`, `kotlin("plugin.spring")` (open class), `kotlin("plugin.jpa")` (no-arg) |
| 외부 API | Google Calendar, Gmail, MS Graph (OAuth 2.0 + Webhook) |
| 테스트 | JUnit 5, MockK, Spring Boot Test, Testcontainers (MySQL 8, Redis) |
| 정적 분석 | Ktlint |
| 직렬화 | `jackson-module-kotlin` |

### 프로젝트 구조 (목표)

```text
src/main/kotlin/com/cheerup/demo/
├── DemoApplication.kt
│
├── global/                    # 공통 인프라
│   ├── aop/                   # @CurrentUser — JWT에서 userId 자동 주입
│   ├── base/                  # BaseEntity (createdAt, updatedAt, deletedAt)
│   ├── config/                # security, redis, ai, mail, swagger
│   ├── exception/             # GlobalExceptionHandler, BusinessException, ErrorCode
│   ├── jwt/                   # JwtProvider, JwtAuthenticationFilter
│   ├── oauth/                 # OAuth2 (Google, Microsoft) 연동
│   ├── response/              # ApiResponse, Meta, ProblemDetails
│   └── scheduler/             # 마감 알림 스케줄러
│
├── application/               # 채용 칸반 보드
├── retrospective/             # 단계별 회고 (커스텀 필드)
├── schedule/                  # 취업 이중 달력 (3레이어)
├── notification/              # 마감 알림 (Redis Sorted Set)
├── mail/                      # Gmail/Outlook OAuth + Webhook 수신
├── ai/                        # Spring AI 분류/추출 엔진, Suggestion
├── auth/                      # 로그인, JWT 발급/갱신
└── user/                      # 사용자 프로필
```

각 도메인 모듈은 `controller/ → service/ → repository/ → domain/ → dto/` 계층 구조를 따른다.

### 아키텍처 패턴

#### 4계층 시스템 구성
제안서의 4계층 모델을 코드 구조에도 반영한다.
1. **입력층** — `controller/` (사용자 직접 입력) + `mail/webhook/` (Gmail/Outlook 메일 수신)
2. **핵심 엔진** — `application/`, `schedule/`, `notification/` (도메인 비즈니스 로직)
3. **AI 보조 엔진** — `ai/` (분류·추출·제안 생성, 항상 `Suggestion` 형태로 출력)
4. **출력층** — `controller/` 응답, FCM/이메일 알림, 캘린더 export

#### Suggestion 패턴 (AI 결과 처리)
AI는 **절대로 데이터를 직접 변경하지 않는다.** 모든 AI 결과는 `Suggestion` 엔티티로 저장되고, 사용자가 `POST /api/mail/suggestions/{id}/accept`를 호출해야 실제 `Application`/`ScheduleEvent`에 반영된다.

```kotlin
// AI 분류 결과 → Suggestion 저장 (DB 미반영)
val suggestion = aiClassifier.classify(mail)
suggestionRepository.save(suggestion)

// 사용자 수락 시에만 도메인 반영
@Transactional
fun accept(suggestionId: Long, userId: Long) {
    val s = suggestionRepository.findOwnedById(suggestionId, userId)
    applicationService.applySuggestion(s)
}
```

#### Kotlin 코드 컨벤션
- **JPA 엔티티**: `class`(open) 필요 — `kotlin-jpa` 플러그인이 no-arg 생성자를 합성. 가변 필드는 `var`로 선언
- **DTO/Request/Response**: 항상 `data class` 사용. `?` 타입으로 nullability 명시
- **불변 우선**: 서비스 파라미터·로컬 변수는 `val` 기본, 컬렉션은 `List`(불변)
- **확장 함수**: 도메인 변환은 `fun Application.toResponse(): ApplicationResponse` 패턴 권장
- **Result/sealed class**: AI 분류 결과처럼 분기되는 응답은 `sealed interface`로 모델링하여 `when` 분기 강제

#### 표준 응답 형식
```kotlin
ApiResponse.success(data)          // { data, meta }
ApiResponse.failure(ErrorCode.X)   // RFC 7807 Problem Details
```

#### 예외 처리
- `GlobalExceptionHandler`(`@RestControllerAdvice`)에서 전역 처리
- `ErrorCode` enum으로 코드/메시지/HTTP 상태 관리
- 도메인 예외는 `BusinessException` 상속

#### 커스텀 필드 처리
사용자가 정의하는 칸반 단계·태그·회고 항목은 정규화된 스키마(핵심 필드) + JSON 컬럼(커스텀 필드)의 하이브리드로 저장한다. 핵심 필드(회사명, 포지션, 단계 등)는 컬럼으로, 사용자 추가 항목은 `customFields: Map<String, Any>` (JSON 컬럼)으로 처리.

#### 사용자 컨텍스트 주입 (AOP)
```kotlin
@PreAuthorize("isAuthenticated()")
@CurrentUser  // JWT에서 userId 자동 주입
fun endpoint(userId: Long): ApiResponse<T> { ... }
```

### 인증 플로우

```text
OAuth2 로그인 (Google/Microsoft)
  → User 생성 또는 조회
  → JWT 발급 (Access 15분 + Refresh 14일, 로테이션)
  → API 요청 시 Authorization: Bearer <token>
  → JwtAuthenticationFilter → @PreAuthorize → @CurrentUser
```

토큰 탈취 감지(동일 Refresh Token 재사용) 시 해당 사용자 전체 세션 무효화.

### 보안 원칙

| 항목 | 정책 |
|------|------|
| OAuth Scope | Gmail/Outlook은 **읽기 전용** scope만 요청 |
| Access Token 저장 | DB 저장 시 AES-256 암호화 |
| 메일 본문 | **저장 금지** — LLM 분석 후 즉시 폐기. 분류 결과·일정 정보만 보관 |
| HTTPS | 전 구간 TLS 1.3 |
| IDOR 방지 | 모든 API에서 요청 `userId`와 리소스 소유자 비교, 불일치 시 403 |
| Rate Limit | 로그인 5회/분/IP, 메일 AI 100회/일/계정 |
| Prompt Injection | 메일 본문은 system 프롬프트와 분리된 user 메시지로 전달, LLM 출력은 JSON 스키마 검증 후 사용 |

### 목표 성능 지표

| 지표 | 목표 |
|------|------|
| 칸반 CRUD API 응답 | p95 ≤ 200ms |
| 월간 달력 조회 | p95 ≤ 300ms |
| 채용 메일 분류 정확도 | F1 ≥ 0.95 |
| AI 제안 수락률 | ≥ 75% |

---

## Build & Run

```bash
# 로컬 DB 실행
docker compose up -d

# 빌드
./gradlew clean build

# 로컬 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# 전체 테스트
./gradlew test

# 단일 테스트
./gradlew test --tests "ClassName"

# Kotlin 코드 스타일 검사
./gradlew ktlintCheck
```

> 현재 로컬 인프라는 `docker-compose.yml`의 MySQL 8 기준으로 맞춘다. Redis는 실제 사용 시점에 추가한다.

---

## 테스트 전략 (제안서 기준)

3계층 테스트 피라미드를 적용하며, GitHub Actions CI에서 PR마다 자동 실행한다.

- **단위 테스트**: JUnit 5 + **MockK**. 서비스 레이어 비즈니스 로직, AI 분류 파싱, 알림 스케줄러 타이밍. **목표 커버리지 80% 이상**
- **통합 테스트**: Spring Boot Test + Testcontainers (MySQL 8, Redis). 칸반 CRUD, 달력 레이어 필터링, 회고 저장 플로우 전체 검증
- **E2E 테스트**: Playwright (프론트엔드 레포). 칸반 드래그앤드롭, 달력 일정 등록, 메일 AI 제안 수락 시나리오
- **AI 분류 정확도 평가**: 채용 메일 200건+ 수동 라벨링 데이터셋 기반 Precision/Recall/F1 측정. 오분류는 Few-shot 예시로 즉시 피드백

---

## Development Checklist

1. 도메인 모듈 구조 준수: `controller/ → service/ → repository/ → domain/ → dto/`
2. 모든 신규 코드는 Kotlin으로 작성 (Java 파일 추가 금지)
3. 인증 필요 엔드포인트에는 `@CurrentUser` + `@PreAuthorize` 적용
4. `@Transactional` 적절히 적용 (조회 전용은 `readOnly = true`)
5. 응답은 `ApiResponse`로 표준화, 오류는 `ErrorCode` enum + RFC 7807
6. **모든 API에서 `userId` 소유권 검증 필수** (IDOR 방지)
7. **AI 결과는 절대 DB에 직접 반영하지 말 것** — 항상 `Suggestion`으로 저장 후 사용자 수락을 거칠 것
8. **채용 메일 본문은 절대 저장하지 말 것** — LLM 호출 후 메모리에서 즉시 폐기
9. OAuth scope는 **최소 권한**(읽기 전용)만 요청, Access Token은 AES-256 암호화 저장
10. 커스텀 필드는 정규화 스키마 + JSON 컬럼 하이브리드로 처리 (핵심 필드는 컬럼, 사용자 정의 항목은 JSON)
11. 시간/마감일은 서버 시각(UTC) 기준으로 저장, 프론트에서 타임존 변환
12. LLM 비용 통제 — Rate Limit(계정당 100회/일) 및 GPT-4o-mini 우선 사용 원칙 준수
13. JPA 엔티티는 `class`(open) + `var`, DTO는 `data class` + `val` 컨벤션 준수
14. 새 도메인 추가 시 본 문서의 "도메인 요약" 표와 "엔드포인트" 표 갱신
