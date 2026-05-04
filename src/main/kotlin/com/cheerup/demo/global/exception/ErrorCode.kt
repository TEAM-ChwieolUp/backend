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

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "지원 정보를 찾을 수 없습니다."),
    STAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "STAGE_NOT_FOUND", "칸반 카테고리를 찾을 수 없습니다."),
    STAGE_NOT_EMPTY(HttpStatus.CONFLICT, "STAGE_NOT_EMPTY", "카드가 있는 칸반 카테고리는 삭제할 수 없습니다."),
    STAGE_FIXED(HttpStatus.CONFLICT, "STAGE_FIXED", "고정된 카테고리는 삭제할 수 없습니다."),
    STAGE_ORDER_PROTECTED(
        HttpStatus.CONFLICT,
        "STAGE_ORDER_PROTECTED",
        "고정된 카테고리는 항상 가장 오른쪽에 있어야 하며, 순서를 바꾸거나 다른 카테고리가 그보다 뒤로 이동할 수 없습니다.",
    ),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "태그를 찾을 수 없습니다."),
    TAG_DUPLICATE(HttpStatus.CONFLICT, "TAG_DUPLICATE", "이미 존재하는 태그 이름입니다."),
    RETROSPECTIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "RETROSPECTIVE_NOT_FOUND", "회고를 찾을 수 없습니다."),
    RETROSPECTIVE_ITEM_INDEX_INVALID(
        HttpStatus.NOT_FOUND,
        "RETROSPECTIVE_ITEM_INDEX_INVALID",
        "회고 항목 인덱스가 올바르지 않습니다.",
    ),
    RETROSPECTIVE_CONCURRENT_MODIFICATION(
        HttpStatus.CONFLICT,
        "RETROSPECTIVE_CONCURRENT_MODIFICATION",
        "다른 요청이 회고를 먼저 수정했습니다. 다시 시도해 주세요.",
    ),
    RETROSPECTIVE_TEMPLATE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "RETROSPECTIVE_TEMPLATE_NOT_FOUND",
        "회고 템플릿을 찾을 수 없습니다.",
    ),
    RETROSPECTIVE_TEMPLATE_DUPLICATE(
        HttpStatus.CONFLICT,
        "RETROSPECTIVE_TEMPLATE_DUPLICATE",
        "이미 존재하는 회고 템플릿 이름입니다.",
    ),
    AI_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI_GENERATION_FAILED", "AI 응답 생성에 실패했습니다."),
    AI_GENERATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI_GENERATION_TIMEOUT", "AI 응답 생성이 시간 내에 완료되지 않았습니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "일정을 찾을 수 없습니다."),
    SCHEDULE_DUPLICATE_JOB_POSTING(
        HttpStatus.CONFLICT,
        "SCHEDULE_DUPLICATE_JOB_POSTING",
        "해당 지원에 대한 일정이 이미 존재합니다.",
    ),
    SCHEDULE_JOB_POSTING_LOCKED(
        HttpStatus.CONFLICT,
        "SCHEDULE_JOB_POSTING_LOCKED",
        "이 일정을 삭제하기 전에 지원 마감일을 먼저 비워주세요.",
    ),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다."),
}
