package com.cheerup.demo.global.config.swagger

import com.cheerup.demo.global.response.ApiResponse
import com.cheerup.demo.global.response.ErrorResponse
import com.cheerup.demo.global.response.Meta
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse as OpenApiResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.core.ResolvableType
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.method.HandlerMethod
import java.lang.reflect.Method
import java.time.Instant

@Component
class SwaggerResponseCustomizer : OperationCustomizer {

    override fun customize(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ): Operation {
        customizeSuccessResponse(operation, handlerMethod)
        customizeErrorResponses(operation, handlerMethod)

        return operation
    }

    private fun customizeSuccessResponse(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ) {
        val payloadType = handlerMethod.apiResponsePayloadType() ?: return
        val payloadClass = payloadType.resolve() ?: return

        val responseCode = operation.responses.keys
            .firstOrNull { it.startsWith("2") }
            ?: "200"
        val existingResponse = operation.responses[responseCode]
        val description = existingResponse?.description ?: "성공"

        operation.responses.addApiResponse(
            responseCode,
            OpenApiResponse()
                .description(description)
                .content(
                    Content().addMediaType(
                        APPLICATION_JSON_VALUE,
                        MediaType().schema(apiResponseSchema(payloadClass)),
                    ),
                ),
        )
    }

    private fun customizeErrorResponses(
        operation: Operation,
        handlerMethod: HandlerMethod,
    ) {
        val annotation = handlerMethod.findMethodAnnotation(SwaggerErrorResponses::class.java)
            ?: return
        val requestPath = handlerMethod.requestPath()

        annotation.errors
            .map { errorResponse ->
                val errorCode = errorResponse.value
                val description = errorResponse.description.ifBlank { errorCode.message }
                ErrorExampleHolder(
                    status = errorCode.status.value(),
                    key = errorCode.name,
                    description = description,
                    example = Example()
                        .description(description)
                        .value(
                            ErrorResponse(
                                code = errorCode.code,
                                message = errorCode.message,
                                detail = description,
                                timestamp = Instant.parse("2026-05-03T00:00:00Z"),
                                path = requestPath,
                            ),
                        ),
                )
            }
            .groupBy { it.status }
            .forEach { (status, examples) ->
                val response = operation.responses[status.toString()]
                    ?: OpenApiResponse()
                val mediaType = response.content
                    ?.get(APPLICATION_JSON_VALUE)
                    ?: MediaType()

                mediaType.schema(refSchema(ErrorResponse::class.java))
                examples.forEach { example ->
                    mediaType.addExamples(example.key, example.example)
                }

                operation.responses.addApiResponse(
                    status.toString(),
                    response
                        .description(examples.joinToString(", ") { it.description })
                        .content(Content().addMediaType(APPLICATION_JSON_VALUE, mediaType)),
                )
            }
    }

    private fun apiResponseSchema(payloadClass: Class<*>): Schema<Any> =
        ObjectSchema().properties(
            mapOf(
                "data" to refSchema(payloadClass),
                "meta" to refSchema(Meta::class.java),
            ),
        )

    private fun refSchema(type: Class<*>): Schema<Any> =
        Schema<Any>().`$ref`("#/components/schemas/${type.simpleName}")

    private fun HandlerMethod.apiResponsePayloadType(): ResolvableType? {
        val returnType = ResolvableType.forMethodReturnType(method)
        val responseType = if (returnType.resolve() == ResponseEntity::class.java) {
            returnType.getGeneric(0)
        } else {
            returnType
        }

        if (responseType.resolve() != ApiResponse::class.java) {
            return null
        }

        val payloadType = responseType.getGeneric(0)
        if (payloadType.type == Unit::class.java || payloadType.type == Unit::class.javaObjectType) {
            return null
        }

        return payloadType
    }

    private fun HandlerMethod.requestPath(): String? {
        val classMapping = beanType.firstRequestMappingPath()
        val methodMapping = method.firstRequestMappingPath()

        return when {
            classMapping == null && methodMapping == null -> null
            classMapping == null -> methodMapping
            methodMapping == null -> classMapping
            else -> "${classMapping.trimEnd('/')}/${methodMapping.trimStart('/')}"
        }
    }

    private fun java.lang.reflect.AnnotatedElement.firstRequestMappingPath(): String? {
        val requestMapping = AnnotatedElementUtils.findMergedAnnotation(this, RequestMapping::class.java)
            ?: return null

        return requestMapping.path.firstOrNull()
            ?: requestMapping.value.firstOrNull()
    }

    private fun <T : Annotation> HandlerMethod.findMethodAnnotation(annotationType: Class<T>): T? =
        AnnotatedElementUtils.findMergedAnnotation(method, annotationType)
            ?: beanType.interfaces
                .asSequence()
                .mapNotNull { interfaceType -> interfaceType.findMatchingMethod(method) }
                .mapNotNull { interfaceMethod ->
                    AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, annotationType)
                }
                .firstOrNull()

    private fun Class<*>.findMatchingMethod(method: Method): Method? =
        methods.firstOrNull { candidate ->
            candidate.name == method.name &&
                candidate.parameterTypes.contentEquals(method.parameterTypes)
        }

    private data class ErrorExampleHolder(
        val status: Int,
        val key: String,
        val description: String,
        val example: Example,
    )
}
