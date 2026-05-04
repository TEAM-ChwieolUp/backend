package com.cheerup.demo.auth.service

import com.cheerup.demo.application.service.StageSeedService
import com.cheerup.demo.auth.dto.LoginResult
import com.cheerup.demo.auth.dto.UserSummary
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.jwt.JwtProvider
import com.cheerup.demo.global.oauth.OAuth2UserInfo
import com.cheerup.demo.user.domain.User
import com.cheerup.demo.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenService: RefreshTokenService,
    private val jwtProvider: JwtProvider,
    private val stageSeedService: StageSeedService,
) {

    @Transactional
    fun loginByOAuth2(userInfo: OAuth2UserInfo): LoginResult {
        val email = userInfo.email
            ?: throw BusinessException(
                ErrorCode.OAUTH2_EMAIL_NOT_PROVIDED,
                detail = "provider=${userInfo.provider}, providerUserId=${userInfo.providerUserId}",
            )
        val existing = userRepository.findByOauth2ProviderAndProviderUserId(
            oauth2Provider = userInfo.provider,
            providerUserId = userInfo.providerUserId,
        )

        val user = if (existing != null) {
            existing.apply {
                this.email = email
                name = userInfo.name
                profileImageUrl = userInfo.profileImageUrl
            }
        } else {
            val created = userRepository.save(
                User(
                    oauth2Provider = userInfo.provider,
                    providerUserId = userInfo.providerUserId,
                    email = email,
                    name = userInfo.name,
                    profileImageUrl = userInfo.profileImageUrl,
                ),
            )
            // 신규 사용자에게 고정 Stage(PASSED/REJECTED)를 시드. 이게 없으면
            // 카드 생성 시 stageId 검증이 항상 실패해 신규 가입자가 차단된다.
            stageSeedService.seedDefault(requireNotNull(created.id) { "User id must not be null after save." })
            created
        }

        return issueTokens(user)
    }

    @Transactional
    fun reissueByRefreshToken(refreshToken: String): LoginResult {
        val userId = refreshTokenService.validateOwnedToken(refreshToken)
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND, detail = "userId=$userId") }

        return issueTokens(user)
    }

    @Transactional
    fun logout(userId: Long) {
        refreshTokenService.deleteByUserId(userId)
    }

    private fun issueTokens(user: User): LoginResult {
        val userId = requireNotNull(user.id) { "User id must not be null when issuing tokens." }
        val refreshToken = refreshTokenService.issue(userId)

        return LoginResult(
            accessToken = jwtProvider.generateAccessToken(userId),
            refreshToken = refreshToken.token,
            refreshTokenMaxAgeSeconds = refreshToken.maxAgeSeconds,
            user = UserSummary(
                id = userId,
                email = user.email,
                name = user.name,
                profileImageUrl = user.profileImageUrl,
            ),
        )
    }
}
