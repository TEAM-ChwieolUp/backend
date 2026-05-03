package com.cheerup.demo.global.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청 한도를 초과했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_TOKEN", "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_NOT_FOUND", "리프레시 토큰을 찾을 수 없습니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISMATCH", "리프레시 토큰이 일치하지 않습니다."),
    OAUTH2_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "OAUTH2_PROVIDER_NOT_SUPPORTED", "지원하지 않는 OAuth2 제공자입니다."),
    OAUTH2_EMAIL_NOT_PROVIDED(HttpStatus.BAD_REQUEST, "OAUTH2_EMAIL_NOT_PROVIDED", "OAuth2 제공자로부터 이메일을 받지 못했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Application was not found."),
    STAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "STAGE_NOT_FOUND", "Stage was not found."),
    STAGE_NOT_EMPTY(HttpStatus.CONFLICT, "STAGE_NOT_EMPTY", "Stage still has applications."),
    STAGE_FIXED(HttpStatus.CONFLICT, "STAGE_FIXED", "Fixed stages cannot be deleted."),
    STAGE_ORDER_PROTECTED(
        HttpStatus.CONFLICT,
        "STAGE_ORDER_PROTECTED",
        "Fixed stages must remain rightmost. Their order cannot change, and other stages cannot move past them.",
    ),
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

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다."),
}
