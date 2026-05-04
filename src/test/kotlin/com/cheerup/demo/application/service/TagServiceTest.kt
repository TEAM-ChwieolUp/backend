package com.cheerup.demo.application.service

import com.cheerup.demo.application.domain.Tag
import com.cheerup.demo.application.dto.CreateTagRequest
import com.cheerup.demo.application.dto.UpdateTagRequest
import com.cheerup.demo.application.repository.TagRepository
import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.util.ReflectionTestUtils

class TagServiceTest {

    private lateinit var tagRepository: TagRepository
    private lateinit var service: TagService

    private val userId = 99L
    private val tagId = 5L

    @BeforeEach
    fun setUp() {
        tagRepository = mockk()
        service = TagService(tagRepository)
    }

    private fun tag(
        id: Long? = tagId,
        ownerId: Long = userId,
        name: String = "원격",
        color: String = "#0EA5E9",
    ): Tag = Tag(userId = ownerId, name = name, color = color).also {
        ReflectionTestUtils.setField(it, "id", id)
    }

    @Test
    @DisplayName("list - 사용자의 태그를 ID 오름차순으로 반환")
    fun list_returnsUsersTags() {
        val t1 = tag(id = 1, name = "A")
        val t2 = tag(id = 2, name = "B")
        every { tagRepository.findAllByUserIdOrderByIdAsc(userId) } returns listOf(t1, t2)

        val result = service.list(userId)

        assertThat(result.map { it.id }).containsExactly(1L, 2L)
        assertThat(result.map { it.name }).containsExactly("A", "B")
    }

    @Test
    @DisplayName("create - 신규 이름이면 저장 후 응답 반환")
    fun create_success() {
        val request = CreateTagRequest(name = "원격", color = "#0EA5E9")
        every { tagRepository.existsByUserIdAndName(userId, "원격") } returns false
        val saveSlot = slot<Tag>()
        every { tagRepository.saveAndFlush(capture(saveSlot)) } answers {
            saveSlot.captured.also { ReflectionTestUtils.setField(it, "id", 10L) }
        }

        val result = service.create(userId, request)

        assertThat(result.id).isEqualTo(10L)
        assertThat(saveSlot.captured.userId).isEqualTo(userId)
        assertThat(saveSlot.captured.name).isEqualTo("원격")
        assertThat(saveSlot.captured.color).isEqualTo("#0EA5E9")
    }

    @Test
    @DisplayName("create - 같은 이름이 이미 존재하면 TAG_DUPLICATE")
    fun create_duplicateName() {
        every { tagRepository.existsByUserIdAndName(userId, "원격") } returns true

        assertThatThrownBy { service.create(userId, CreateTagRequest("원격", "#0EA5E9")) }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.TAG_DUPLICATE }

        verify(exactly = 0) { tagRepository.saveAndFlush(any<Tag>()) }
    }

    @Test
    @DisplayName("create - 동시성으로 DB UNIQUE 위반 발생 시 TAG_DUPLICATE 변환")
    fun create_dbUniqueRaceTranslated() {
        every { tagRepository.existsByUserIdAndName(userId, "원격") } returns false
        every { tagRepository.saveAndFlush(any<Tag>()) } throws DataIntegrityViolationException("uk_tags_user_id_name")

        assertThatThrownBy { service.create(userId, CreateTagRequest("원격", "#0EA5E9")) }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.TAG_DUPLICATE }
    }

    @Test
    @DisplayName("update - 미존재 태그면 TAG_NOT_FOUND")
    fun update_notFound() {
        every { tagRepository.findByIdAndUserId(tagId, userId) } returns null

        assertThatThrownBy { service.update(userId, tagId, UpdateTagRequest(name = "x")) }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.TAG_NOT_FOUND }
    }

    @Test
    @DisplayName("update - 다른 사용자 태그 접근은 TAG_NOT_FOUND (IDOR 방지)")
    fun update_otherUsersTag_notFound() {
        every { tagRepository.findByIdAndUserId(tagId, userId) } returns null

        assertThatThrownBy { service.update(userId, tagId, UpdateTagRequest(color = "#222222")) }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.TAG_NOT_FOUND }
    }

    @Test
    @DisplayName("update - 이름/색상 변경 후 응답 반환")
    fun update_success() {
        val existing = tag(name = "원격", color = "#0EA5E9")
        every { tagRepository.findByIdAndUserId(tagId, userId) } returns existing
        every { tagRepository.existsByUserIdAndNameAndIdNot(userId, "하이브리드", tagId) } returns false
        every { tagRepository.saveAndFlush(existing) } returns existing

        val result = service.update(userId, tagId, UpdateTagRequest(name = "하이브리드", color = "#22C55E"))

        assertThat(result.name).isEqualTo("하이브리드")
        assertThat(result.color).isEqualTo("#22C55E")
        assertThat(existing.name).isEqualTo("하이브리드")
        assertThat(existing.color).isEqualTo("#22C55E")
    }

    @Test
    @DisplayName("update - 같은 이름으로 변경(no-op)은 중복 검사 스킵")
    fun update_sameName_skipsDuplicateCheck() {
        val existing = tag(name = "원격", color = "#0EA5E9")
        every { tagRepository.findByIdAndUserId(tagId, userId) } returns existing
        every { tagRepository.saveAndFlush(existing) } returns existing

        service.update(userId, tagId, UpdateTagRequest(name = "원격", color = "#222222"))

        verify(exactly = 0) { tagRepository.existsByUserIdAndNameAndIdNot(any(), any(), any()) }
        assertThat(existing.color).isEqualTo("#222222")
    }

    @Test
    @DisplayName("update - 변경 이름이 이미 존재하면 TAG_DUPLICATE")
    fun update_renameToDuplicate() {
        val existing = tag(name = "원격")
        every { tagRepository.findByIdAndUserId(tagId, userId) } returns existing
        every { tagRepository.existsByUserIdAndNameAndIdNot(userId, "하이브리드", tagId) } returns true

        assertThatThrownBy { service.update(userId, tagId, UpdateTagRequest(name = "하이브리드")) }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.TAG_DUPLICATE }

        verify(exactly = 0) { tagRepository.saveAndFlush(any<Tag>()) }
    }

    @Test
    @DisplayName("update - 빈 본문이면 변경 없이 현재 값 반환")
    fun update_emptyBody_noChange() {
        val existing = tag(name = "원격", color = "#0EA5E9")
        every { tagRepository.findByIdAndUserId(tagId, userId) } returns existing
        every { tagRepository.saveAndFlush(existing) } returns existing

        val result = service.update(userId, tagId, UpdateTagRequest())

        assertThat(result.name).isEqualTo("원격")
        assertThat(result.color).isEqualTo("#0EA5E9")
    }

    @Test
    @DisplayName("delete - 미존재 태그면 TAG_NOT_FOUND")
    fun delete_notFound() {
        every { tagRepository.findByIdAndUserId(tagId, userId) } returns null

        assertThatThrownBy { service.delete(userId, tagId) }
            .isInstanceOf(BusinessException::class.java)
            .matches { (it as BusinessException).errorCode == ErrorCode.TAG_NOT_FOUND }
    }

    @Test
    @DisplayName("delete - 성공 시 repository.delete 호출")
    fun delete_success() {
        val existing = tag()
        every { tagRepository.findByIdAndUserId(tagId, userId) } returns existing
        every { tagRepository.delete(existing) } returns Unit

        service.delete(userId, tagId)

        verify(exactly = 1) { tagRepository.delete(existing) }
    }
}
