package com.cheerup.demo.application.service

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.domain.ApplicationTag
import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.dto.ApplicationCard
import com.cheerup.demo.application.dto.ApplicationResponse
import com.cheerup.demo.application.dto.BoardResponse
import com.cheerup.demo.application.dto.CreateApplicationRequest
import com.cheerup.demo.application.dto.TagSummary
import com.cheerup.demo.application.dto.UpdateApplicationRequest
import com.cheerup.demo.application.dto.toCard
import com.cheerup.demo.application.dto.toNode
import com.cheerup.demo.application.dto.toResponse
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.ApplicationTagRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.application.repository.TagRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import com.cheerup.demo.notification.service.NoOpNotificationQueue
import com.cheerup.demo.notification.service.NotificationQueue
import com.cheerup.demo.schedule.service.NoOpScheduleSyncService
import com.cheerup.demo.schedule.service.ScheduleSyncService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ApplicationService(
    private val stageRepository: StageRepository,
    private val applicationRepository: ApplicationRepository,
    private val applicationTagRepository: ApplicationTagRepository,
    private val tagRepository: TagRepository,
    private val scheduleSyncService: ScheduleSyncService = NoOpScheduleSyncService,
    private val notificationQueue: NotificationQueue = NoOpNotificationQueue,
) {

    fun getBoard(
        userId: Long,
        stageId: Long?,
        tagId: Long?,
        priority: Priority?,
    ): BoardResponse {
        val stages = stageRepository.findAllByUserIdOrderByDisplayOrderAsc(userId)
        val cards = applicationRepository.findBoardCards(userId, stageId, tagId, priority)
        val tagsByApplicationId = loadTagsByApplicationId(cards, userId)

        val cardsByStageId: Map<Long, List<ApplicationCard>> =
            cards.groupBy(
                keySelector = { it.stageId }, // 무엇을 기준으로 묶을거니
                valueTransform = { application -> // 묶을 때 값을 어떤 형태로 바꿀거니
                    val applicationId = requireNotNull(application.id) { "Application must be persisted" }
                    application.toCard(tagsByApplicationId[applicationId].orEmpty())
                },
            )

        // 모든 단계를 돌면서 해당 단계에 맞는 코드를 넣기
        val nodes = stages.map { stage ->
            val stageId = requireNotNull(stage.id) { "Stage must be persisted" }
            stage.toNode(cardsByStageId[stageId].orEmpty())
        }

        return BoardResponse(nodes)
    }

    @Transactional
    fun createApplication(userId: Long, request: CreateApplicationRequest): ApplicationCard {
        stageRepository.findByIdAndUserId(request.stageId, userId)
            ?: throw BusinessException(ErrorCode.STAGE_NOT_FOUND, detail = "stageId=${request.stageId}")

        val tagSummaries = resolveTags(request.tagIds, userId)

        val application = Application(
            userId = userId,
            stageId = request.stageId,
            companyName = request.companyName,
            position = request.position,
            appliedAt = request.appliedAt,
            deadlineAt = request.deadlineAt,
            noResponseDays = request.noResponseDays,
            priority = request.priority,
            memo = request.memo,
            jobPostingUrl = request.jobPostingUrl,
        )
        val saved = applicationRepository.save(application)
        val savedId = requireNotNull(saved.id) { "Application must be persisted" }

        if (tagSummaries.isNotEmpty()) {
            val joins = tagSummaries.map { ApplicationTag(applicationId = savedId, tagId = it.id) }
            applicationTagRepository.saveAll(joins)
        }

        scheduleSyncService.syncApplicationDeadline(
            userId = userId,
            applicationId = savedId,
            companyName = saved.companyName,
            deadlineAt = saved.deadlineAt,
        )
        saved.deadlineAt?.let { deadlineAt ->
            notificationQueue.enqueueApplicationDeadline(
                userId = userId,
                applicationId = savedId,
                deadlineAt = deadlineAt,
            )
        }

        return saved.toCard(tagSummaries)
    }

    @Transactional
    fun update(
        userId: Long,
        applicationId: Long,
        request: UpdateApplicationRequest,
    ): ApplicationResponse {
        val application = applicationRepository.findByIdAndUserId(applicationId, userId)
            ?: throw BusinessException(
                ErrorCode.APPLICATION_NOT_FOUND,
                detail = "applicationId=$applicationId",
            )

        // 빈 본문 단축: 검증/변경/외부 동기화 모두 스킵, 현재 상태로 응답
        if (request.isEmpty()) {
            return application.toResponse(currentTagSummaries(applicationId, userId))
        }

        // stageId 검증 (변경 요청이 있을 때만)
        request.stageId?.let { newStageId ->
            stageRepository.findByIdAndUserId(newStageId, userId)
                ?: throw BusinessException(ErrorCode.STAGE_NOT_FOUND, detail = "stageId=$newStageId")
        }

        // tagIds 검증 (요청이 있고 비어있지 않을 때만 — 비어있으면 모두 제거 케이스)
        if (request.tagIds != null && request.tagIds.isNotEmpty()) {
            val distinctIds = request.tagIds.toSet()
            val tags = tagRepository.findAllByIdInAndUserId(distinctIds, userId)
            if (tags.size != distinctIds.size) {
                throw BusinessException(ErrorCode.TAG_NOT_FOUND, detail = "tagIds=$distinctIds")
            }
        }

        // 필드별 부분 수정 — null이면 변경 없음. 도메인 메서드로 캡슐화.
        request.stageId?.let { application.changeStage(it) }
        request.companyName?.let { application.companyName = it }
        request.position?.let { application.position = it }
        request.appliedAt?.let { application.appliedAt = it }
        val deadlineChanged = request.deadlineAt?.let { application.setDeadline(it) } ?: false
        request.noResponseDays?.let { application.noResponseDays = it }
        request.priority?.let { application.priority = it }
        request.memo?.let { application.updateMemo(it) }
        request.jobPostingUrl?.let { application.jobPostingUrl = it }

        // deadline 자체가 바뀌었거나, 회사명 변경 + 기존 마감 존재(ScheduleEvent title 갱신 필요)
        val shouldSyncDeadline = deadlineChanged ||
            (request.companyName != null && application.deadlineAt != null)
        if (shouldSyncDeadline) {
            scheduleSyncService.syncApplicationDeadline(
                userId = userId,
                applicationId = applicationId,
                companyName = application.companyName,
                deadlineAt = application.deadlineAt,
            )
        }

        if (deadlineChanged) {
            application.deadlineAt?.let { deadlineAt ->
                notificationQueue.updateApplicationDeadline(
                    userId = userId,
                    applicationId = applicationId,
                    deadlineAt = deadlineAt,
                )
            } ?: notificationQueue.removeByApplicationId(userId, applicationId)
        }

        // 태그 diff 갱신 (tagIds == null이면 변경 없음)
        if (request.tagIds != null) {
            applyTagDiff(applicationId, request.tagIds)
        }

        // 응답용 태그 목록 재조회
        return application.toResponse(currentTagSummaries(applicationId, userId))
    }

    private fun currentTagSummaries(applicationId: Long, userId: Long): List<TagSummary> =
        applicationTagRepository
            .findTagViewsByApplicationIds(listOf(applicationId), userId)
            .map { TagSummary(id = it.tagId, name = it.tagName, color = it.tagColor) }

    @Transactional
    fun deleteApplication(userId: Long, applicationId: Long) {
        val application = applicationRepository.findByIdAndUserId(applicationId, userId)
            ?: throw BusinessException(
                ErrorCode.APPLICATION_NOT_FOUND,
                detail = "applicationId=$applicationId",
            )

        notificationQueue.removeByApplicationId(userId, applicationId)
        applicationTagRepository.deleteByApplicationId(applicationId)
        scheduleSyncService.deleteByApplicationId(userId, applicationId)

        applicationRepository.delete(application)
    }

    private fun applyTagDiff(applicationId: Long, requestedTagIds: List<Long>) {
        val currentTagIds = applicationTagRepository
            .findAllByApplicationId(applicationId)
            .map { it.tagId }
            .toSet()
        val requested = requestedTagIds.toSet()

        val toRemove = currentTagIds - requested
        if (toRemove.isNotEmpty()) {
            applicationTagRepository.deleteByApplicationIdAndTagIdIn(applicationId, toRemove)
        }

        val toAdd = requested - currentTagIds
        if (toAdd.isNotEmpty()) {
            val joins = toAdd.map { tagId ->
                ApplicationTag(applicationId = applicationId, tagId = tagId)
            }
            applicationTagRepository.saveAll(joins)
        }
    }

    private fun resolveTags(tagIds: List<Long>, userId: Long): List<TagSummary> {
        if (tagIds.isEmpty()) {
            return emptyList()
        }

        val distinctIds = tagIds.toSet()
        val tags = tagRepository.findAllByIdInAndUserId(distinctIds, userId)
        if (tags.size != distinctIds.size) {
            throw BusinessException(ErrorCode.TAG_NOT_FOUND, detail = "tagIds=$distinctIds")
        }

        return tags.map { tag ->
            TagSummary(
                id = requireNotNull(tag.id) { "Tag must be persisted" },
                name = tag.name,
                color = tag.color,
            )
        }
    }

    private fun loadTagsByApplicationId(
        cards: List<Application>,
        userId: Long,
    ): Map<Long, List<TagSummary>> {
        if (cards.isEmpty()) {
            return emptyMap()
        }

        val applicationIds = cards.mapNotNull { it.id }
        val views = applicationTagRepository.findTagViewsByApplicationIds(applicationIds, userId)

        return views.groupBy({ it.applicationId }) { view ->
            TagSummary(id = view.tagId, name = view.tagName, color = view.tagColor)
        }
    }
}
