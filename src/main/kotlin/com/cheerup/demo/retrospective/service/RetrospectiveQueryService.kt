package com.cheerup.demo.retrospective.service

import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.retrospective.dto.RetrospectiveListResponse
import com.cheerup.demo.retrospective.dto.RetrospectiveResponse
import com.cheerup.demo.retrospective.dto.toResponse
import com.cheerup.demo.retrospective.dto.toSummary
import com.cheerup.demo.retrospective.repository.RetrospectiveRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RetrospectiveQueryService(
    private val retrospectiveRepository: RetrospectiveRepository,
    private val applicationRepository: ApplicationRepository,
) {

    fun getByApplication(userId: Long, applicationId: Long): RetrospectiveListResponse {
        applicationRepository.findByIdAndUserId(applicationId, userId)
            ?: throw BusinessException(
                ErrorCode.APPLICATION_NOT_FOUND,
                detail = "applicationId=$applicationId",
            )

        val summaries = retrospectiveRepository
            .findAllByApplicationIdAndUserIdOrderByCreatedAtAsc(applicationId, userId)
            .map { it.toSummary() }

        return RetrospectiveListResponse(summaries)
    }

    fun getOne(userId: Long, retrospectiveId: Long): RetrospectiveResponse {
        val retrospective = retrospectiveRepository.findByIdAndUserId(retrospectiveId, userId)
            ?: throw BusinessException(
                ErrorCode.RETROSPECTIVE_NOT_FOUND,
                detail = "retrospectiveId=$retrospectiveId",
            )
        return retrospective.toResponse()
    }
}
