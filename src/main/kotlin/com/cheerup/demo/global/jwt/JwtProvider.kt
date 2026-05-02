package com.cheerup.demo.global.jwt

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
) {

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    fun generateAccessToken(userId: Long): String =
        generateToken(
            userId = userId,
            tokenType = ACCESS_TOKEN_TYPE,
            expiresAt = calculateAccessTokenExpiry(),
        )

    fun generateRefreshToken(userId: Long): String =
        generateToken(
            userId = userId,
            tokenType = REFRESH_TOKEN_TYPE,
            expiresAt = calculateRefreshTokenExpiry(),
        )

    fun calculateAccessTokenExpiry(): Instant = Instant.now().plus(jwtProperties.accessTokenExpiration)

    fun calculateRefreshTokenExpiry(): Instant = Instant.now().plus(jwtProperties.refreshTokenExpiration)

    fun refreshTokenMaxAgeSeconds(): Long = jwtProperties.refreshTokenExpiration.seconds

    fun parseUserId(token: String): Long =
        parseClaims(token).subject.toLong()

    fun validateAccessToken(token: String) {
        validateToken(token, ACCESS_TOKEN_TYPE)
    }

    fun validateRefreshToken(token: String) {
        validateToken(token, REFRESH_TOKEN_TYPE)
    }

    private fun generateToken(
        userId: Long,
        tokenType: String,
        expiresAt: Instant,
    ): String = Jwts.builder()
        .subject(userId.toString())
        .issuer(jwtProperties.issuer)
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(expiresAt))
        .claim(TOKEN_TYPE_CLAIM, tokenType)
        .signWith(secretKey)
        .compact()

    private fun validateToken(
        token: String,
        expectedType: String,
    ) {
        val claims = parseClaims(token)
        val tokenType = claims[TOKEN_TYPE_CLAIM] as? String

        if (tokenType != expectedType) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }
    }

    private fun parseClaims(token: String): Claims =
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: ExpiredJwtException) {
            throw BusinessException(ErrorCode.EXPIRED_TOKEN)
        } catch (_: JwtException) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }

    companion object {
        private const val TOKEN_TYPE_CLAIM: String = "type"
        private const val ACCESS_TOKEN_TYPE: String = "access"
        private const val REFRESH_TOKEN_TYPE: String = "refresh"
    }
}
