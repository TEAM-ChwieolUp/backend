# user — 사용자 계정

OAuth 2.0 기반 사용자 계정 관리. JWT 발급/갱신, OAuth 콜백 처리는 Phase 2에서 `auth/` 도메인으로 분리.

## 엔티티

### User

| 필드 | 타입 | 제약 |
|---|---|---|
| `id` | `Long` | PK |
| `provider` | `OauthProvider` | NOT NULL, STRING 매핑 |
| `providerId` | `String` | NOT NULL, **`(provider, providerId)` UNIQUE** |
| `email` | `String` | NOT NULL |
| `displayName` | `String` | NOT NULL |
| `profileImageUrl` | `String?` | |
| `status` | `UserStatus` | NOT NULL, default `ACTIVE` |

```kotlin
enum class OauthProvider { GOOGLE, MICROSOFT }
enum class UserStatus { ACTIVE, WITHDRAWN }
```

`providerId`는 OAuth subject claim. 같은 이메일이라도 provider가 다르면 별개 계정으로 취급.

## 가입 흐름

```
OAuth 로그인 (Google/Microsoft)
  → providerId로 기존 User 조회
  → 없으면 신규 User 생성
  → 신규 가입 시 application.StageSeedService.seedDefault(userId) 호출
    → 기본 Stage 5개 자동 생성
```

기본 Stage seed (실제 생성은 `application/` 도메인 책임):

| order | name | category |
|---|---|---|
| 0 | 관심 기업 | IN_PROGRESS |
| 1 | 서류 전형 | IN_PROGRESS |
| 2 | 면접 | IN_PROGRESS |
| 3 | 최종 합격 | PASSED |
| 4 | 불합격 | REJECTED |

## 탈퇴 흐름

```kotlin
@Transactional
fun withdraw(userId: Long) {
    val user = userRepository.findById(userId)
        ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
    user.status = UserStatus.WITHDRAWN
    // 외부 시스템 연동 정리는 별도 Service 호출:
    // - 외부 캘린더 export 취소
    // - Redis 알림 큐 비우기
    // - OAuth 토큰 폐기
}
```

`User` 자체의 hard delete는 grace period(예: 30일) 후 batch job에서 처리:

```kotlin
@Scheduled(cron = "0 0 4 * * *")
fun purgeWithdrawnUsers() {
    userRepository.findWithdrawnBefore(Instant.now().minus(30, DAYS))
        .forEach { userRepository.delete(it) }
    // → DB FK CASCADE로 Stage, Application, Tag, Retrospective, ScheduleEvent 자동 삭제
}
```

## 다른 도메인이 User를 참조하는 방식

- 모든 도메인 엔티티에 `userId: Long` (val) 컬럼
- DDL: `FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE`
- JPA `@ManyToOne` 매핑은 사용하지 않음

## API 요약

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/users/me` | 내 프로필 조회 |
| PATCH | `/api/users/me` | 프로필 수정 (`displayName` 등) |
| DELETE | `/api/users/me` | 탈퇴 (`status = WITHDRAWN`) |

## Phase 2

| 항목 | 내용 |
|---|---|
| `RefreshToken` 엔티티 | JWT Refresh Token 로테이션 체인 (탈취 감지) |
| `auth/` 패키지 | OAuth 콜백, JWT 발급/갱신 분리 |
| `@CurrentUser` | JWT에서 userId 자동 주입 AOP |
