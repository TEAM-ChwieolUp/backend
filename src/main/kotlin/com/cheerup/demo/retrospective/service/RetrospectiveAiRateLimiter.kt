package com.cheerup.demo.retrospective.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface RetrospectiveAiRateLimiter {
    fun tryAcquire(userId: Long): Boolean
}

@Component
class InMemoryRetrospectiveAiRateLimiter(
    @Value("\${cheerup.retrospective.ai.daily-limit:50}")
    private val dailyLimit: Int,
) : RetrospectiveAiRateLimiter {

    private val counters = ConcurrentHashMap<CounterKey, AtomicInteger>()

    override fun tryAcquire(userId: Long): Boolean {
        if (dailyLimit <= 0) return false

        val today = LocalDate.now(ZoneOffset.UTC)
        counters.keys.removeIf { it.date.isBefore(today.minusDays(1)) }

        val counter = counters.computeIfAbsent(CounterKey(userId, today)) { AtomicInteger(0) }
        while (true) {
            val current = counter.get()
            if (current >= dailyLimit) return false
            if (counter.compareAndSet(current, current + 1)) return true
        }
    }

    private data class CounterKey(
        val userId: Long,
        val date: LocalDate,
    )
}
