package com.cheerup.demo.application.service

import com.cheerup.demo.application.domain.Stage
import com.cheerup.demo.application.domain.StageCategory
import com.cheerup.demo.application.repository.StageRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StageSeedServiceTest {

    private lateinit var stageRepository: StageRepository
    private lateinit var service: StageSeedService

    private val userId = 99L

    @BeforeEach
    fun setUp() {
        stageRepository = mockk()
        service = StageSeedService(stageRepository)
    }

    @Test
    @DisplayName("seedDefault - 신규 사용자: PASSED(0), REJECTED(1) 두 Stage 저장")
    fun seedDefault_newUser_savesPassedAndRejected() {
        every { stageRepository.existsByUserIdAndCategory(userId, StageCategory.PASSED) } returns false
        val savedSlot = slot<Stage>()
        val savedSlots = mutableListOf<Stage>()
        every { stageRepository.save(capture(savedSlot)) } answers {
            savedSlots += savedSlot.captured
            savedSlot.captured
        }

        service.seedDefault(userId)

        verify(exactly = 2) { stageRepository.save(any<Stage>()) }
        assertThat(savedSlots).hasSize(2)

        val passed = savedSlots.first { it.category == StageCategory.PASSED }
        val rejected = savedSlots.first { it.category == StageCategory.REJECTED }

        assertThat(passed.userId).isEqualTo(userId)
        assertThat(passed.displayOrder).isEqualTo(0)
        assertThat(passed.name).isNotBlank()

        assertThat(rejected.userId).isEqualTo(userId)
        assertThat(rejected.displayOrder).isEqualTo(1)
        assertThat(rejected.name).isNotBlank()
    }

    @Test
    @DisplayName("seedDefault - 이미 시드된 사용자: save 호출 없음 (멱등성)")
    fun seedDefault_alreadySeeded_isIdempotent() {
        every { stageRepository.existsByUserIdAndCategory(userId, StageCategory.PASSED) } returns true

        service.seedDefault(userId)

        verify(exactly = 0) { stageRepository.save(any<Stage>()) }
    }
}
