package com.cheerup.demo.application.service

import com.cheerup.demo.application.domain.Application
import com.cheerup.demo.application.domain.ApplicationTag
import com.cheerup.demo.application.domain.Priority
import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.application.domain.Tag
import com.cheerup.demo.application.dto.UpdateApplicationRequest
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.ApplicationTagRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.application.repository.TagRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class ApplicationServiceTest {

    private lateinit var stageRepository: StageRepository
    private lateinit var applicationRepository: ApplicationRepository
    private lateinit var applicationTagRepository: ApplicationTagRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var service: ApplicationService

    private val userId = 99L
    private val applicationId = 101L

    @BeforeEach
    fun setUp() {
        stageRepository = mockk()
        applicationRepository = mockk()
        applicationTagRepository = mockk(relaxUnitFun = true)
        tagRepository = mockk()
        service = ApplicationService(
            stageRepository = stageRepository,
            applicationRepository = applicationRepository,
            applicationTagRepository = applicationTagRepository,
            tagRepository = tagRepository,
        )
    }

    @Test
    @DisplayName("update - 카드 미존재 시 APPLICATION_NOT_FOUND")
    fun update_applicationNotFound() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns null

        val request = UpdateApplicationRequest(memo = "변경")

        assertThatThrownBy { service.update(userId, applicationId, request) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND)
            })
    }

    @Test
    @DisplayName("update - stageId 변경 성공 시 stageId가 업데이트되고 응답에 반영")
    fun update_stageIdChange_success() {
        val application = fixtureApplication(stageId = 1L)
        val newStage = fixtureStage(id = 3L)

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every { stageRepository.findByIdAndUserId(3L, userId) } returns newStage
        every {
            applicationTagRepository.findTagViewsByApplicationIds(listOf(applicationId), userId)
        } returns emptyList()

        val request = UpdateApplicationRequest(stageId = 3L)
        val response = service.update(userId, applicationId, request)

        assertThat(application.stageId).isEqualTo(3L)
        assertThat(response.stageId).isEqualTo(3L)
        assertThat(response.id).isEqualTo(applicationId)
        assertThat(response.tags).isEmpty()
    }

    @Test
    @DisplayName("update - 다른 사용자 stageId 지정 시 STAGE_NOT_FOUND")
    fun update_stageIdOtherUser_stageNotFound() {
        val application = fixtureApplication(stageId = 1L)

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every { stageRepository.findByIdAndUserId(7L, userId) } returns null

        val request = UpdateApplicationRequest(stageId = 7L)

        assertThatThrownBy { service.update(userId, applicationId, request) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.STAGE_NOT_FOUND)
            })
    }

    @Test
    @DisplayName("update - tagIds에 다른 사용자 태그 포함 시 TAG_NOT_FOUND")
    fun update_tagIdsOtherUser_tagNotFound() {
        val application = fixtureApplication(stageId = 1L)

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        // 요청한 tagIds = [10, 11], DB에서는 그 중 1개만 사용자 소유
        every { tagRepository.findAllByIdInAndUserId(setOf(10L, 11L), userId) } returns
            listOf(fixtureTag(id = 10L))

        val request = UpdateApplicationRequest(tagIds = listOf(10L, 11L))

        assertThatThrownBy { service.update(userId, applicationId, request) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.TAG_NOT_FOUND)
            })
    }

    @Test
    @DisplayName("update - tagIds 갱신 diff (현재 [1,2] 요청 [2,3] -> 1 제거, 3 추가)")
    fun update_tagIds_diff() {
        val application = fixtureApplication(stageId = 1L)

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every { tagRepository.findAllByIdInAndUserId(setOf(2L, 3L), userId) } returns
            listOf(fixtureTag(id = 2L), fixtureTag(id = 3L))
        every { applicationTagRepository.findAllByApplicationId(applicationId) } returns
            listOf(
                ApplicationTag(applicationId = applicationId, tagId = 1L),
                ApplicationTag(applicationId = applicationId, tagId = 2L),
            )
        every {
            applicationTagRepository.findTagViewsByApplicationIds(listOf(applicationId), userId)
        } returns emptyList()

        val savedSlot = slot<List<ApplicationTag>>()
        every { applicationTagRepository.saveAll(capture(savedSlot)) } answers {
            savedSlot.captured
        }

        val request = UpdateApplicationRequest(tagIds = listOf(2L, 3L))
        service.update(userId, applicationId, request)

        verify(exactly = 1) {
            applicationTagRepository.deleteByApplicationIdAndTagIdIn(applicationId, setOf(1L))
        }
        verify(exactly = 1) { applicationTagRepository.saveAll(any<List<ApplicationTag>>()) }
        assertThat(savedSlot.captured).hasSize(1)
        assertThat(savedSlot.captured[0].tagId).isEqualTo(3L)
    }

    @Test
    @DisplayName("update - tagIds = emptyList 모든 태그 제거")
    fun update_tagIds_emptyList_removesAll() {
        val application = fixtureApplication(stageId = 1L)

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every { applicationTagRepository.findAllByApplicationId(applicationId) } returns
            listOf(
                ApplicationTag(applicationId = applicationId, tagId = 1L),
                ApplicationTag(applicationId = applicationId, tagId = 2L),
            )
        every {
            applicationTagRepository.findTagViewsByApplicationIds(listOf(applicationId), userId)
        } returns emptyList()

        val request = UpdateApplicationRequest(tagIds = emptyList())
        service.update(userId, applicationId, request)

        verify(exactly = 1) {
            applicationTagRepository.deleteByApplicationIdAndTagIdIn(applicationId, setOf(1L, 2L))
        }
        verify(exactly = 0) { applicationTagRepository.saveAll(any<List<ApplicationTag>>()) }
        verify(exactly = 0) { tagRepository.findAllByIdInAndUserId(any(), any()) }
    }

    @Test
    @DisplayName("update - tagIds = null 태그 관련 호출 일절 없음")
    fun update_tagIds_null_noTagCalls() {
        val application = fixtureApplication(stageId = 1L)

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every {
            applicationTagRepository.findTagViewsByApplicationIds(listOf(applicationId), userId)
        } returns emptyList()

        val request = UpdateApplicationRequest(memo = "변경")
        service.update(userId, applicationId, request)

        verify(exactly = 0) { applicationTagRepository.findAllByApplicationId(any()) }
        verify(exactly = 0) {
            applicationTagRepository.deleteByApplicationIdAndTagIdIn(any(), any())
        }
        verify(exactly = 0) { applicationTagRepository.saveAll(any<List<ApplicationTag>>()) }
        verify(exactly = 0) { tagRepository.findAllByIdInAndUserId(any(), any()) }
    }

    @Test
    @DisplayName("update - 빈 본문 모든 필드 null 변경 없이 현재 상태 응답")
    fun update_emptyBody_noChanges() {
        val application = fixtureApplication(
            stageId = 1L,
            companyName = "토스",
            position = "Backend",
            memo = "기존 메모",
            priority = Priority.HIGH,
        )

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every {
            applicationTagRepository.findTagViewsByApplicationIds(listOf(applicationId), userId)
        } returns emptyList()

        val response = service.update(userId, applicationId, UpdateApplicationRequest())

        assertThat(application.stageId).isEqualTo(1L)
        assertThat(application.companyName).isEqualTo("토스")
        assertThat(application.position).isEqualTo("Backend")
        assertThat(application.memo).isEqualTo("기존 메모")
        assertThat(application.priority).isEqualTo(Priority.HIGH)

        assertThat(response.id).isEqualTo(applicationId)
        assertThat(response.companyName).isEqualTo("토스")
        assertThat(response.priority).isEqualTo(Priority.HIGH)

        verify(exactly = 0) { stageRepository.findByIdAndUserId(any(), any()) }
        verify(exactly = 0) { applicationTagRepository.findAllByApplicationId(any()) }
    }

    @Test
    @DisplayName("update - memo와 priority 부분 수정 반영")
    fun update_memoAndPriority() {
        val application = fixtureApplication(
            stageId = 1L,
            memo = "이전",
            priority = Priority.NORMAL,
        )

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every {
            applicationTagRepository.findTagViewsByApplicationIds(listOf(applicationId), userId)
        } returns emptyList()

        val request = UpdateApplicationRequest(memo = "면접 일정 5/15", priority = Priority.HIGH)
        val response = service.update(userId, applicationId, request)

        assertThat(application.memo).isEqualTo("면접 일정 5/15")
        assertThat(application.priority).isEqualTo(Priority.HIGH)
        assertThat(response.memo).isEqualTo("면접 일정 5/15")
        assertThat(response.priority).isEqualTo(Priority.HIGH)
    }

    @Test
    @DisplayName("update - deadlineAt 부분 수정 반영")
    fun update_deadlineAt() {
        val application = fixtureApplication(stageId = 1L, deadlineAt = null)
        val newDeadline = Instant.parse("2026-05-15T07:00:00Z")

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every {
            applicationTagRepository.findTagViewsByApplicationIds(listOf(applicationId), userId)
        } returns emptyList()

        val request = UpdateApplicationRequest(deadlineAt = newDeadline)
        val response = service.update(userId, applicationId, request)

        assertThat(application.deadlineAt).isEqualTo(newDeadline)
        assertThat(response.deadlineAt).isEqualTo(newDeadline)
    }

    @Test
    @DisplayName("delete - 카드 미존재 시 APPLICATION_NOT_FOUND")
    fun delete_applicationNotFound() {
        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns null

        assertThatThrownBy { service.deleteApplication(userId, applicationId) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND)
            })

        verify(exactly = 0) { applicationTagRepository.deleteByApplicationId(any()) }
        verify(exactly = 0) { applicationRepository.delete(any<Application>()) }
    }

    @Test
    @DisplayName("delete - 다른 사용자 카드는 APPLICATION_NOT_FOUND (IDOR)")
    fun delete_otherUsersApplication_applicationNotFound() {
        // findByIdAndUserId 는 (applicationId, userId) 조합으로만 매칭.
        // 다른 사용자의 카드라면 이 조합에서는 null 이 반환되어야 한다.
        val otherUserId = 200L
        every { applicationRepository.findByIdAndUserId(applicationId, otherUserId) } returns null

        assertThatThrownBy { service.deleteApplication(otherUserId, applicationId) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.APPLICATION_NOT_FOUND)
            })

        verify(exactly = 0) { applicationTagRepository.deleteByApplicationId(any()) }
        verify(exactly = 0) { applicationRepository.delete(any<Application>()) }
    }

    @Test
    @DisplayName("delete - 정상 삭제 시 application_tags 정리 후 application 삭제 순서")
    fun delete_success_orderingApplicationTagsThenApplication() {
        val application = fixtureApplication(stageId = 1L)

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every { applicationRepository.delete(application) } returns Unit

        service.deleteApplication(userId, applicationId)

        verifyOrder {
            applicationRepository.findByIdAndUserId(applicationId, userId)
            applicationTagRepository.deleteByApplicationId(applicationId)
            applicationRepository.delete(application)
        }
    }

    @Test
    @DisplayName("delete - 태그 연결이 없어도 deleteByApplicationId 는 항상 호출")
    fun delete_noTagJoins_stillCallsDeleteByApplicationId() {
        val application = fixtureApplication(stageId = 1L)

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every { applicationRepository.delete(application) } returns Unit

        service.deleteApplication(userId, applicationId)

        // 영향 행 0이어도 벌크 delete 쿼리는 안전하게 한 번 실행되어야 한다.
        verify(exactly = 1) { applicationTagRepository.deleteByApplicationId(applicationId) }
        verify(exactly = 1) { applicationRepository.delete(application) }
    }

    @Test
    @DisplayName("delete - 정상 삭제는 예외 없이 종료 (반환 타입 Unit)")
    fun delete_success_returnsUnitWithoutException() {
        val application = fixtureApplication(stageId = 1L)

        every { applicationRepository.findByIdAndUserId(applicationId, userId) } returns application
        every { applicationRepository.delete(application) } returns Unit

        assertThatCode { service.deleteApplication(userId, applicationId) }
            .doesNotThrowAnyException()
    }

    private fun fixtureApplication(
        id: Long = applicationId,
        stageId: Long,
        companyName: String = "토스",
        position: String = "Backend",
        appliedAt: Instant? = null,
        deadlineAt: Instant? = null,
        noResponseDays: Int? = 7,
        priority: Priority = Priority.NORMAL,
        memo: String? = null,
        jobPostingUrl: String? = null,
    ): Application {
        val application = Application(
            userId = userId,
            stageId = stageId,
            companyName = companyName,
            position = position,
            appliedAt = appliedAt,
            deadlineAt = deadlineAt,
            noResponseDays = noResponseDays,
            priority = priority,
            memo = memo,
            jobPostingUrl = jobPostingUrl,
        )
        ReflectionTestUtils.setField(application, "id", id)
        return application
    }

    private fun fixtureStage(id: Long, ownerId: Long = userId): Stage {
        val stage = Stage(
            userId = ownerId,
            name = "서류 전형",
            displayOrder = 0,
            color = "#888888",
            category = StageCategory.IN_PROGRESS,
        )
        ReflectionTestUtils.setField(stage, "id", id)
        return stage
    }

    private fun fixtureTag(id: Long, ownerId: Long = userId): Tag {
        val tag = Tag(userId = ownerId, name = "tag$id", color = "#00aa00")
        ReflectionTestUtils.setField(tag, "id", id)
        return tag
    }
}
