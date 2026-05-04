package com.cheerup.demo.application.service

import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.application.dto.CreateStageRequest
import com.cheerup.demo.application.dto.UpdateStageRequest
import com.cheerup.demo.application.repository.ApplicationRepository
import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils

class StageServiceTest {

    private lateinit var stageRepository: StageRepository
    private lateinit var applicationRepository: ApplicationRepository
    private lateinit var service: StageService

    private val userId = 99L

    @BeforeEach
    fun setUp() {
        stageRepository = mockk()
        applicationRepository = mockk()
        service = StageService(stageRepository, applicationRepository)
    }

    // ────────────────── create ──────────────────

    @Test
    @DisplayName("create - 시드만 있는 보드: 새 IN_PROGRESS는 displayOrder=0, PASSED/REJECTED는 +1 시프트")
    fun create_freshBoard_insertsAtPassedSlotAndShiftsFixed() {
        val passed = stageFixture(id = 10L, displayOrder = 0, category = StageCategory.PASSED)
        val rejected = stageFixture(id = 11L, displayOrder = 1, category = StageCategory.REJECTED)

        every { stageRepository.findTopByUserIdOrderByDisplayOrderDesc(userId) } returns rejected
        every { stageRepository.findByUserIdAndCategory(userId, StageCategory.PASSED) } returns passed
        every {
            stageRepository.shiftDisplayOrder(userId, from = 0, to = 1, delta = 1, excludeId = null)
        } returns 2

        val savedSlot = slot<Stage>()
        every { stageRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val response = service.create(userId, CreateStageRequest(name = "서류 전형", color = "#4F46E5"))

        verify(exactly = 1) {
            stageRepository.shiftDisplayOrder(userId, from = 0, to = 1, delta = 1, excludeId = null)
        }
        assertThat(savedSlot.captured.displayOrder).isEqualTo(0)
        assertThat(savedSlot.captured.category).isEqualTo(StageCategory.IN_PROGRESS)
        assertThat(savedSlot.captured.name).isEqualTo("서류 전형")
        assertThat(response.displayOrder).isEqualTo(0)
    }

    @Test
    @DisplayName("create - 명시 displayOrder 가 PASSED 자리를 초과하면 PASSED 자리로 클램프")
    fun create_explicitOrderOverPassed_clampedToPassedSlot() {
        // 보드: IN_PROGRESS(0), IN_PROGRESS(1), PASSED(2), REJECTED(3)
        val passed = stageFixture(id = 10L, displayOrder = 2, category = StageCategory.PASSED)
        val rejected = stageFixture(id = 11L, displayOrder = 3, category = StageCategory.REJECTED)

        every { stageRepository.findTopByUserIdOrderByDisplayOrderDesc(userId) } returns rejected
        every { stageRepository.findByUserIdAndCategory(userId, StageCategory.PASSED) } returns passed
        every {
            stageRepository.shiftDisplayOrder(userId, from = 2, to = 3, delta = 1, excludeId = null)
        } returns 2

        val savedSlot = slot<Stage>()
        every { stageRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        // 사용자가 displayOrder=99 로 보냈지만 PASSED.displayOrder=2 로 클램프되어야 한다.
        service.create(userId, CreateStageRequest(name = "기술 면접", color = "#0EA5E9", displayOrder = 99))

        verify(exactly = 1) {
            stageRepository.shiftDisplayOrder(userId, from = 2, to = 3, delta = 1, excludeId = null)
        }
        assertThat(savedSlot.captured.displayOrder).isEqualTo(2)
    }

    @Test
    @DisplayName("create - PASSED 미시드(레거시) 사용자: 기존 max+1 폴백")
    fun create_legacyNoPassed_fallsBackToMaxPlusOne() {
        every { stageRepository.findTopByUserIdOrderByDisplayOrderDesc(userId) } returns null
        every { stageRepository.findByUserIdAndCategory(userId, StageCategory.PASSED) } returns null

        val savedSlot = slot<Stage>()
        every { stageRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        service.create(userId, CreateStageRequest(name = "관심 기업", color = "#94A3B8"))

        // 기존 행 없으니 시프트 호출 없음, displayOrder = 0 (-1 + 1)
        verify(exactly = 0) { stageRepository.shiftDisplayOrder(any(), any(), any(), any(), any()) }
        assertThat(savedSlot.captured.displayOrder).isEqualTo(0)
    }

    // ────────────────── update ──────────────────

    @Test
    @DisplayName("update - PASSED 의 displayOrder 변경 시도 → STAGE_ORDER_PROTECTED")
    fun update_passedDisplayOrder_throwsOrderProtected() {
        val passed = stageFixture(id = 10L, displayOrder = 5, category = StageCategory.PASSED)
        every { stageRepository.findByIdAndUserId(10L, userId) } returns passed

        assertThatThrownBy {
            service.update(userId, 10L, UpdateStageRequest(displayOrder = 0))
        }.isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.STAGE_ORDER_PROTECTED)
            })
    }

    @Test
    @DisplayName("update - REJECTED 의 displayOrder 변경 시도 → STAGE_ORDER_PROTECTED")
    fun update_rejectedDisplayOrder_throwsOrderProtected() {
        val rejected = stageFixture(id = 11L, displayOrder = 6, category = StageCategory.REJECTED)
        every { stageRepository.findByIdAndUserId(11L, userId) } returns rejected

        assertThatThrownBy {
            service.update(userId, 11L, UpdateStageRequest(displayOrder = 1))
        }.isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.STAGE_ORDER_PROTECTED)
            })
    }

    @Test
    @DisplayName("update - IN_PROGRESS 가 PASSED 위치(또는 너머)로 이동 시도 → STAGE_ORDER_PROTECTED")
    fun update_inProgressReorderPastPassed_throwsOrderProtected() {
        // 보드: IN_PROGRESS(0)[id=1], IN_PROGRESS(1)[id=2], PASSED(2), REJECTED(3)
        val target = stageFixture(id = 1L, displayOrder = 0, category = StageCategory.IN_PROGRESS)
        val passed = stageFixture(id = 10L, displayOrder = 2, category = StageCategory.PASSED)
        val rejected = stageFixture(id = 11L, displayOrder = 3, category = StageCategory.REJECTED)

        every { stageRepository.findByIdAndUserId(1L, userId) } returns target
        every { stageRepository.findByUserIdAndCategory(userId, StageCategory.PASSED) } returns passed
        every { stageRepository.findTopByUserIdOrderByDisplayOrderDesc(userId) } returns rejected

        // PASSED.displayOrder = 2 → maxAllowed = 1. 사용자가 2 또는 3 으로 옮기려 하면 거부.
        assertThatThrownBy {
            service.update(userId, 1L, UpdateStageRequest(displayOrder = 2))
        }.isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.STAGE_ORDER_PROTECTED)
            })
    }

    @Test
    @DisplayName("update - IN_PROGRESS 정상 reorder (왼쪽으로 이동): 사이 행 +1 시프트 후 자기 위치 갱신")
    fun update_inProgressReorderUp_success() {
        // 보드: IN_PROGRESS(0), IN_PROGRESS(1)[target], IN_PROGRESS(2), PASSED(3), REJECTED(4)
        val target = stageFixture(id = 5L, displayOrder = 1, category = StageCategory.IN_PROGRESS)
        val passed = stageFixture(id = 10L, displayOrder = 3, category = StageCategory.PASSED)
        val rejected = stageFixture(id = 11L, displayOrder = 4, category = StageCategory.REJECTED)

        every { stageRepository.findByIdAndUserId(5L, userId) } returns target
        every { stageRepository.findByUserIdAndCategory(userId, StageCategory.PASSED) } returns passed
        every { stageRepository.findTopByUserIdOrderByDisplayOrderDesc(userId) } returns rejected
        every {
            stageRepository.shiftDisplayOrder(userId, from = 0, to = 0, delta = 1, excludeId = 5L)
        } returns 1

        service.update(userId, 5L, UpdateStageRequest(displayOrder = 0))

        verify(exactly = 1) {
            stageRepository.shiftDisplayOrder(userId, from = 0, to = 0, delta = 1, excludeId = 5L)
        }
        assertThat(target.displayOrder).isEqualTo(0)
    }

    @Test
    @DisplayName("update - PASSED 의 name/color 만 변경: order 검사 안 거침, 통과")
    fun update_passedNameAndColor_succeedsWithoutOrderCheck() {
        val passed = stageFixture(id = 10L, displayOrder = 5, category = StageCategory.PASSED)
        every { stageRepository.findByIdAndUserId(10L, userId) } returns passed

        val response = service.update(
            userId,
            10L,
            UpdateStageRequest(name = "최종 합격 🎉", color = "#16A34A"),
        )

        assertThat(passed.name).isEqualTo("최종 합격 🎉")
        assertThat(passed.color).isEqualTo("#16A34A")
        assertThat(response.name).isEqualTo("최종 합격 🎉")
        // displayOrder 는 건드리지 않음
        assertThat(passed.displayOrder).isEqualTo(5)

        verify(exactly = 0) {
            stageRepository.findByUserIdAndCategory(any(), any())
        }
    }

    @Test
    @DisplayName("update - 빈 본문: 변경 없음 + repo 호출 최소화")
    fun update_emptyBody_noChanges() {
        val stage = stageFixture(id = 5L, displayOrder = 1, category = StageCategory.IN_PROGRESS, name = "기존")
        every { stageRepository.findByIdAndUserId(5L, userId) } returns stage

        val response = service.update(userId, 5L, UpdateStageRequest())

        assertThat(stage.name).isEqualTo("기존")
        assertThat(response.name).isEqualTo("기존")

        verify(exactly = 0) { stageRepository.findByUserIdAndCategory(any(), any()) }
        verify(exactly = 0) { stageRepository.findTopByUserIdOrderByDisplayOrderDesc(any()) }
    }

    @Test
    @DisplayName("update - Stage 미존재: STAGE_NOT_FOUND")
    fun update_stageMissing_throwsNotFound() {
        every { stageRepository.findByIdAndUserId(99L, userId) } returns null

        assertThatThrownBy {
            service.update(userId, 99L, UpdateStageRequest(name = "변경"))
        }.isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.STAGE_NOT_FOUND)
            })
    }

    // ────────────────── delete ──────────────────

    @Test
    @DisplayName("delete - PASSED 삭제 시도 → STAGE_FIXED")
    fun delete_passed_throwsStageFixed() {
        val passed = stageFixture(id = 10L, displayOrder = 5, category = StageCategory.PASSED)
        every { stageRepository.findByIdAndUserId(10L, userId) } returns passed

        assertThatThrownBy { service.delete(userId, 10L) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.STAGE_FIXED)
            })

        verify(exactly = 0) { stageRepository.delete(any<Stage>()) }
    }

    @Test
    @DisplayName("delete - REJECTED 삭제 시도 → STAGE_FIXED")
    fun delete_rejected_throwsStageFixed() {
        val rejected = stageFixture(id = 11L, displayOrder = 6, category = StageCategory.REJECTED)
        every { stageRepository.findByIdAndUserId(11L, userId) } returns rejected

        assertThatThrownBy { service.delete(userId, 11L) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.STAGE_FIXED)
            })
    }

    @Test
    @DisplayName("delete - IN_PROGRESS 카드 잔존: STAGE_NOT_EMPTY")
    fun delete_inProgressWithCards_throwsNotEmpty() {
        val stage = stageFixture(id = 5L, displayOrder = 1, category = StageCategory.IN_PROGRESS)
        every { stageRepository.findByIdAndUserId(5L, userId) } returns stage
        every { applicationRepository.countByUserIdAndStageId(userId, 5L) } returns 3

        assertThatThrownBy { service.delete(userId, 5L) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies({ ex ->
                assertThat((ex as BusinessException).errorCode).isEqualTo(ErrorCode.STAGE_NOT_EMPTY)
            })

        verify(exactly = 0) { stageRepository.delete(any<Stage>()) }
    }

    @Test
    @DisplayName("delete - IN_PROGRESS 카드 0개: 정상 삭제")
    fun delete_inProgressEmpty_success() {
        val stage = stageFixture(id = 5L, displayOrder = 1, category = StageCategory.IN_PROGRESS)
        every { stageRepository.findByIdAndUserId(5L, userId) } returns stage
        every { applicationRepository.countByUserIdAndStageId(userId, 5L) } returns 0
        every { stageRepository.delete(stage) } returns Unit

        assertThatCode { service.delete(userId, 5L) }.doesNotThrowAnyException()

        verify(exactly = 1) { stageRepository.delete(stage) }
    }

    // ────────────────── helpers ──────────────────

    private fun stageFixture(
        id: Long,
        displayOrder: Int,
        category: StageCategory,
        name: String = "stage-$id",
        color: String = "#888888",
    ): Stage {
        val stage = Stage(
            userId = userId,
            name = name,
            displayOrder = displayOrder,
            color = color,
            category = category,
        )
        ReflectionTestUtils.setField(stage, "id", id)
        return stage
    }
}
