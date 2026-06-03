package com.cheerup.demo.mail.classifier

import com.cheerup.demo.global.exception.BusinessException
import com.cheerup.demo.global.exception.ErrorCode
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "app.mail.classifier", name = ["mode"], havingValue = "ai")
class AiRecruitmentMailClassifier : RecruitmentMailClassifier {

    override fun classify(command: RecruitmentMailClassificationCommand): RecruitmentMailClassificationResult {
        throw BusinessException(
            ErrorCode.AI_GENERATION_FAILED,
            detail = "AI mail classifier is not implemented yet.",
        )
    }
}
