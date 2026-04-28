package com.cheerup.demo.global.response

import org.slf4j.MDC
import java.time.Instant

data class Meta(
    val timestamp: Instant = Instant.now(),
    val requestId: String? = MDC.get("requestId"),
)
