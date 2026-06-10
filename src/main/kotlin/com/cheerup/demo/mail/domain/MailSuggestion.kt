package com.cheerup.demo.mail.domain

import com.cheerup.demo.global.base.BaseEntity
import com.cheerup.demo.global.persistence.StringListConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "mail_suggestions",
    indexes = [
        Index(name = "idx_mail_suggestions_user_status_id", columnList = "user_id,status,id"),
        Index(
            name = "idx_mail_suggestions_lookup",
            columnList = "user_id,integration_id,message_id,application_id,status",
        ),
    ],
)
class MailSuggestion(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "integration_id", nullable = false)
    val integrationId: Long,

    @Column(name = "message_id", nullable = false, length = 255)
    val messageId: String,

    @Column(name = "application_id", nullable = false)
    val applicationId: Long,

    @Column(name = "predicted_stage_id")
    var predictedStageId: Long? = null,

    @Column(name = "predicted_stage_name", length = 100)
    var predictedStageName: String? = null,

    @Column(name = "classification_confidence", nullable = false)
    var classificationConfidence: Double,

    @Column(name = "classification_reason", nullable = false, length = 1000)
    var classificationReason: String,

    @Convert(converter = StringListConverter::class)
    @Column(name = "classification_evidence", nullable = false, columnDefinition = "TEXT")
    var classificationEvidence: MutableList<String> = mutableListOf(),

    @Column(name = "from_stage_id", nullable = false)
    var fromStageId: Long,

    @Column(name = "from_stage_name", nullable = false, length = 100)
    var fromStageName: String,

    @Column(name = "to_stage_id")
    var toStageId: Long? = null,

    @Column(name = "to_stage_name", length = 100)
    var toStageName: String? = null,

    @Column(name = "move_confidence", nullable = false)
    var moveConfidence: Double,

    @Column(name = "move_reason", nullable = false, length = 1000)
    var moveReason: String,

    @Convert(converter = StringListConverter::class)
    @Column(name = "move_evidence", nullable = false, columnDefinition = "TEXT")
    var moveEvidence: MutableList<String> = mutableListOf(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MailSuggestionStatus,

    @Column(name = "processed_at")
    var processedAt: Instant? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun accept(now: Instant) {
        status = MailSuggestionStatus.ACCEPTED
        processedAt = now
    }

    fun reject(now: Instant) {
        status = MailSuggestionStatus.REJECTED
        processedAt = now
    }
}
