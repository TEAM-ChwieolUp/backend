package com.cheerup.demo.mail.service

import com.cheerup.demo.application.repository.StageRepository
import com.cheerup.demo.mail.classifier.RecruitmentMailClassificationCommand
import com.cheerup.demo.mail.classifier.RecruitmentMailClassifier
import com.cheerup.demo.mail.classifier.StageCandidate
import com.cheerup.demo.mail.client.MailClientRegistry
import com.cheerup.demo.mail.client.MailClientProperties
import com.cheerup.demo.mail.client.MailIntegrationContext
import com.cheerup.demo.mail.client.toContext
import com.cheerup.demo.mail.domain.MailProvider
import com.cheerup.demo.mail.dto.ClassifiedMailMessagesResponse
import com.cheerup.demo.mail.dto.toResponse
import com.cheerup.demo.mail.oauth.MailTokenCipher
import com.cheerup.demo.mail.repository.MailIntegrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MailQueryService(
    private val mailClientRegistry: MailClientRegistry,
    private val classifier: RecruitmentMailClassifier,
    private val stageRepository: StageRepository,
    private val mailIntegrationRepository: MailIntegrationRepository,
    private val mailTokenCipher: MailTokenCipher,
    private val mailClientProperties: MailClientProperties,
) {

    fun listClassifiedMessages(
        userId: Long,
        limit: Int,
    ): ClassifiedMailMessagesResponse {
        val normalizedLimit = limit.coerceAtLeast(1)
        val stages = stageRepository.findAllByUserIdOrderByDisplayOrderAsc(userId)
            .map {
                StageCandidate(
                    id = requireNotNull(it.id) { "Stage must be persisted" },
                    name = it.name,
                    category = it.category,
                )
            }

        val integrations = loadActiveIntegrations(userId)

        val messages = integrations
            .flatMap { integration ->
                val mailClient = mailClientRegistry.get(integration.provider)
                mailClient.listMessages(integration, normalizedLimit)
            }
            .sortedByDescending { it.receivedAt }
            .take(normalizedLimit)
            .map { message ->
                val classification = classifier.classify(
                    RecruitmentMailClassificationCommand(
                        message = message,
                        stages = stages,
                    ),
                )
                message.toResponse(classification)
            }

        return ClassifiedMailMessagesResponse(messages)
    }

    private fun loadActiveIntegrations(userId: Long): List<MailIntegrationContext> {
        val persisted = mailIntegrationRepository.findAllByUserIdAndActiveTrueOrderByIdAsc(userId)
            .map {
                it.toContext().copy(
                    accessToken = mailTokenCipher.decrypt(it.accessToken),
                    refreshToken = mailTokenCipher.decrypt(it.refreshToken),
                )
            }
        return persisted.ifEmpty {
            if (mailClientProperties.clients.google == "stub") {
                listOf(
                    MailIntegrationContext(
                        integrationId = null,
                        userId = userId,
                        provider = MailProvider.GOOGLE,
                        providerAccountId = "stub-google-account",
                        email = "stub.google@example.com",
                        displayName = "Stub Google Mail",
                        accessToken = null,
                        refreshToken = null,
                        expiresAt = null,
                        scope = null,
                    ),
                )
            } else {
                emptyList()
            }
        }
    }
}
