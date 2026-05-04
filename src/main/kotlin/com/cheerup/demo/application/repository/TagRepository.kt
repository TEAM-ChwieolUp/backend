package com.cheerup.demo.application.repository

import com.cheerup.demo.application.domain.Tag
import org.springframework.data.jpa.repository.JpaRepository

interface TagRepository : JpaRepository<Tag, Long> {

    fun findAllByUserIdOrderByIdAsc(userId: Long): List<Tag>

    fun findByIdAndUserId(id: Long, userId: Long): Tag?

    fun findAllByIdInAndUserId(ids: Collection<Long>, userId: Long): List<Tag>

    fun existsByUserIdAndName(userId: Long, name: String): Boolean

    fun existsByUserIdAndNameAndIdNot(userId: Long, name: String, id: Long): Boolean
}
