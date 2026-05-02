package com.cheerup.demo.global.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid request value."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Application was not found."),
    STAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "STAGE_NOT_FOUND", "Stage was not found."),
    STAGE_NOT_EMPTY(HttpStatus.CONFLICT, "STAGE_NOT_EMPTY", "Stage still has applications."),
    STAGE_FIXED(HttpStatus.CONFLICT, "STAGE_FIXED", "Fixed stages cannot be deleted."),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "Tag was not found."),
    TAG_DUPLICATE(HttpStatus.CONFLICT, "TAG_DUPLICATE", "Tag name already exists."),
    RETROSPECTIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "RETROSPECTIVE_NOT_FOUND", "Retrospective was not found."),
    RETROSPECTIVE_ITEM_INDEX_INVALID(
        HttpStatus.NOT_FOUND,
        "RETROSPECTIVE_ITEM_INDEX_INVALID",
        "Retrospective item index is invalid.",
    ),
    RETROSPECTIVE_CONCURRENT_MODIFICATION(
        HttpStatus.CONFLICT,
        "RETROSPECTIVE_CONCURRENT_MODIFICATION",
        "Retrospective was modified by another request. Please retry.",
    ),
    RETROSPECTIVE_TEMPLATE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "RETROSPECTIVE_TEMPLATE_NOT_FOUND",
        "Retrospective template was not found.",
    ),
    RETROSPECTIVE_TEMPLATE_DUPLICATE(
        HttpStatus.CONFLICT,
        "RETROSPECTIVE_TEMPLATE_DUPLICATE",
        "Retrospective template name already exists.",
    ),
    AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED", "AI response generation failed."),
    AI_GENERATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI_GENERATION_TIMEOUT", "AI response generation timed out."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Schedule event was not found."),
    SCHEDULE_DUPLICATE_JOB_POSTING(
        HttpStatus.CONFLICT,
        "SCHEDULE_DUPLICATE_JOB_POSTING",
        "Schedule event already exists for this application.",
    ),
    SCHEDULE_JOB_POSTING_LOCKED(
        HttpStatus.CONFLICT,
        "SCHEDULE_JOB_POSTING_LOCKED",
        "Clear the application deadline before deleting this schedule event.",
    ),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error."),
}
