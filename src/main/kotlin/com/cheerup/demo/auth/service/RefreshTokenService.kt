package com.cheerup.demo.auth.service

import com.cheerup.demo.auth.domain.RefreshToken
import com.cheerup.demo.auth.repository.RefreshTokenRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.global.jwt.JwtProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProvider: JwtProvider,
) {

    @Transactional
    fun issue(userId: Long): IssuedRefreshToken {
        val token = jwtProvider.generateRefreshToken(userId)
        val expiresAt = jwtProvider.calculateRefreshTokenExpiry()
        val savedToken = refreshTokenRepository.findByUserId(userId)

        if (savedToken == null) {
            refreshTokenRepository.save(
                RefreshToken(
                    userId = userId,
                    token = token,
                    expiresAt = expiresAt,
                ),
            )
        } else {
            savedToken.token = token
            savedToken.expiresAt = expiresAt
        }

        return IssuedRefreshToken(
            token = token,
            maxAgeSeconds = jwtProvider.refreshTokenMaxAgeSeconds(),
        )
    }

    @Transactional(readOnly = true)
    fun validateOwnedToken(token: String): Long {
        jwtProvider.validateRefreshToken(token)
        val userId = jwtProvider.parseUserId(token)
        val savedToken = refreshTokenRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)

        if (savedToken.token != token) {
            throw BusinessException(ErrorCode.REFRESH_TOKEN_MISMATCH)
        }

        if (savedToken.expiresAt.isBefore(Instant.now())) {
            throw BusinessException(ErrorCode.EXPIRED_TOKEN)
        }

        return userId
    }

    @Transactional
    fun deleteByUserId(userId: Long) {
        refreshTokenRepository.deleteByUserId(userId)
    }
}

data class IssuedRefreshToken(
    val token: String,
    val maxAgeSeconds: Long,
)
