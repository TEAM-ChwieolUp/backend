package com.cheerup.demo.auth.service

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
) {

    @Transactional
    fun loginByOAuth2(userInfo: OAuth2UserInfo): LoginResult {
        val email = userInfo.email
            ?: throw BusinessException(
                ErrorCode.OAUTH2_EMAIL_NOT_PROVIDED,
                detail = "provider=${userInfo.provider}, providerUserId=${userInfo.providerUserId}",
            )
        val user = userRepository.findByOauth2ProviderAndProviderUserId(
            oauth2Provider = userInfo.provider,
            providerUserId = userInfo.providerUserId,
        )?.apply {
            this.email = email
            name = userInfo.name
            profileImageUrl = userInfo.profileImageUrl
        } ?: userRepository.save(
            User(
                oauth2Provider = userInfo.provider,
                providerUserId = userInfo.providerUserId,
                email = email,
                name = userInfo.name,
                profileImageUrl = userInfo.profileImageUrl,
            ),
        )

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
