package com.cheerup.demo.global.exception

import com.cheerup.demo.global.response.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(
        ex: BusinessException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        errorResponse(
            errorCode = ex.errorCode,
            detail = ex.detail,
            request = request,
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        errorResponse(
            errorCode = ErrorCode.INVALID_INPUT,
            detail = ex.fieldErrorDetail(),
            request = request,
        )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        errorResponse(
            errorCode = ErrorCode.INVALID_INPUT,
            detail = ex.constraintViolationDetail(),
            request = request,
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        errorResponse(
            errorCode = ErrorCode.INVALID_INPUT,
            detail = "요청 본문을 읽을 수 없습니다.",
            request = request,
        )

    @ExceptionHandler(Exception::class)
    fun handleUnknown(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return errorResponse(
            errorCode = ErrorCode.INTERNAL_ERROR,
            detail = null,
            request = request,
        )
    }

    private fun errorResponse(
        errorCode: ErrorCode,
        detail: String?,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val body = ErrorResponse(
            code = errorCode.code,
            message = errorCode.message,
            detail = detail,
            path = request.requestURI,
        )

        return ResponseEntity.status(errorCode.status).body(body)
    }

    private fun MethodArgumentNotValidException.fieldErrorDetail(): String? {
        val errors = bindingResult.fieldErrors
        if (errors.isEmpty()) {
            return null
        }

        return errors.joinToString("; ") { error ->
            val reason = error.defaultMessage ?: "invalid value"
            "${error.field}: $reason"
        }
    }

    private fun ConstraintViolationException.constraintViolationDetail(): String? {
        if (constraintViolations.isEmpty()) {
            return null
        }

        return constraintViolations.joinToString("; ") { violation ->
            "${violation.propertyPath}: ${violation.message}"
        }
    }
}
