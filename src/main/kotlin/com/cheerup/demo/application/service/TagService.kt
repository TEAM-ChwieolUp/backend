package com.cheerup.demo.application.service

import com.cheerup.demo.application.domain.Tag
import com.cheerup.demo.application.dto.CreateTagRequest
import com.cheerup.demo.application.dto.TagResponse
import com.cheerup.demo.application.dto.UpdateTagRequest
import com.cheerup.demo.application.dto.toTagResponse
import com.cheerup.demo.application.repository.TagRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TagService(
    private val tagRepository: TagRepository,
) {

    fun list(userId: Long): List<TagResponse> =
        tagRepository.findAllByUserIdOrderByIdAsc(userId)
            .map { it.toTagResponse() }

    @Transactional
    fun create(userId: Long, request: CreateTagRequest): TagResponse {
        if (tagRepository.existsByUserIdAndName(userId, request.name)) {
            throw BusinessException(ErrorCode.TAG_DUPLICATE, detail = "name=${request.name}")
        }

        val tag = Tag(userId = userId, name = request.name, color = request.color)
        return saveOrTranslateDuplicate(tag).toTagResponse()
    }

    @Transactional
    fun update(userId: Long, tagId: Long, request: UpdateTagRequest): TagResponse {
        val tag = tagRepository.findByIdAndUserId(tagId, userId)
            ?: throw BusinessException(ErrorCode.TAG_NOT_FOUND, detail = "tagId=$tagId")

        // 빈 본문 단축: DB write 없이 현재 값 반환
        if (request.isEmpty()) return tag.toTagResponse()

        request.name?.let { newName ->
            if (newName != tag.name &&
                tagRepository.existsByUserIdAndNameAndIdNot(userId, newName, tagId)
            ) {
                throw BusinessException(ErrorCode.TAG_DUPLICATE, detail = "name=$newName")
            }
            tag.name = newName
        }
        request.color?.let { tag.color = it }

        return try {
            tagRepository.saveAndFlush(tag).toTagResponse()
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.TAG_DUPLICATE, detail = "name=${tag.name}", cause = e)
        }
    }

    @Transactional
    fun delete(userId: Long, tagId: Long) {
        val tag = tagRepository.findByIdAndUserId(tagId, userId)
            ?: throw BusinessException(ErrorCode.TAG_NOT_FOUND, detail = "tagId=$tagId")

        tagRepository.delete(tag)
    }

    private fun saveOrTranslateDuplicate(tag: Tag): Tag =
        try {
            tagRepository.saveAndFlush(tag)
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.TAG_DUPLICATE, detail = "name=${tag.name}", cause = e)
        }
}
