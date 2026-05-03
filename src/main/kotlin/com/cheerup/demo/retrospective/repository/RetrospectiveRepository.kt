package com.cheerup.demo.retrospective.repository

import com.cheerup.demo.retrospective.domain.Retrospective
import org.springframework.data.jpa.repository.JpaRepository

interface RetrospectiveRepository : JpaRepository<Retrospective, Long> {

    fun findByIdAndUserId(id: Long, userId: Long): Retrospective?

    fun findAllByApplicationIdAndUserIdOrderByCreatedAtAsc(
        applicationId: Long,
        userId: Long,
    ): List<Retrospective>
}
