package com.cheerup.demo.mail.ai

import com.cheerup.demo.ai.client.AiServerProperties
import com.cheerup.demo.ai.client.callAiServer
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class KanbanMoveRecommender(
    private val restClient: RestClient,
    private val properties: AiServerProperties,
) {
    fun recommend(request: KanbanMoveRecommendRequest): KanbanMoveRecommendResponse =
        callAiServer("AI kanban move recommendation") {
            restClient.post()
                .uri(properties.kanbanMoveRecommendPath)
                .body(request)
                .retrieve()
                .requiredBody(KanbanMoveRecommendResponse::class.java)
                .also { response ->
                    requireNotNull(response.recommendMove) { "Missing recommend_move" }
                    requireNotNull(response.fromStage) { "Missing from_stage" }
                    requireNotNull(response.confidence) { "Missing confidence" }
                    require(!response.reason.isNullOrBlank()) { "Missing reason" }
                    require(response.needsUserConfirmation == true) { "User confirmation must be required" }
                    if (response.recommendMove) {
                        requireNotNull(response.toStage) { "Missing to_stage" }
                    }
                }
        }
}
