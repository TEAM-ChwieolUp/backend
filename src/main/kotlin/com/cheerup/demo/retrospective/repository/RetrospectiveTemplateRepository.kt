package com.cheerup.demo.retrospective.repository

import com.cheerup.demo.retrospective.domain.RetrospectiveTemplate
import org.springframework.data.jpa.repository.JpaRepository

interface RetrospectiveTemplateRepository : JpaRepository<RetrospectiveTemplate, Long> {

    fun findAllByUserIdOrderByIdAsc(userId: Long): List<RetrospectiveTemplate>

    fun findByIdAndUserId(id: Long, userId: Long): RetrospectiveTemplate?

    fun existsByUserIdAndName(userId: Long, name: String): Boolean

    fun existsByUserIdAndNameAndIdNot(userId: Long, name: String, id: Long): Boolean
}
