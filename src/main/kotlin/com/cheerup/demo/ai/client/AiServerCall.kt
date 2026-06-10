package com.cheerup.demo.ai.client

import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException

inline fun <T> callAiServer(operation: String, call: () -> T): T =
    try {
        call()
    } catch (ex: ResourceAccessException) {
        if (ex.containsTimeout()) {
            throw AiServerTimeoutException("$operation timed out.", ex)
        }
        throw AiServerException("$operation request failed.", ex)
    } catch (ex: RestClientResponseException) {
        throw AiServerException("$operation returned HTTP ${ex.statusCode.value()}.", ex)
    } catch (ex: RestClientException) {
        throw AiServerException("$operation response was invalid.", ex)
    } catch (ex: AiServerException) {
        throw ex
    } catch (ex: AiServerTimeoutException) {
        throw ex
    } catch (ex: RuntimeException) {
        throw AiServerException("$operation failed.", ex)
    }

fun Throwable.containsTimeout(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SocketTimeoutException || current is HttpTimeoutException) {
            return true
        }
        val message = current.message?.lowercase()
        if (message?.contains("timeout") == true || message?.contains("timed out") == true) {
            return true
        }
        current = current.cause
    }
    return false
}
