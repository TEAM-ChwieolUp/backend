package com.cheerup.demo.auth.repository

import com.cheerup.demo.auth.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByUserId(userId: Long): RefreshToken?

    fun deleteByUserId(userId: Long)
}
