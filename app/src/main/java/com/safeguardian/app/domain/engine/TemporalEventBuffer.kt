package com.safeguardian.app.domain.engine

import com.safeguardian.app.domain.model.*

/**
 * Maintains a short temporal event buffer for multi-event reasoning.
 * Events are automatically pruned based on age.
 * Thread-safe via synchronized access.
 */
class TemporalEventBuffer(
    private val maxBufferSize: Int = 100,
    private val maxEventAgeMs: Long = 60_000L
) {
    private val events = mutableListOf<TemporalEvent>()

    data class TemporalEvent(
        val type: EventType,
        val score: Float,
        val timestamp: Long = System.currentTimeMillis(),
        val metadata: String = ""
    )

    enum class EventType {
        MOTION_ANOMALY,
        AUDIO_EVENT,
        VISION_EVENT,
        FUSION_RESULT,
        STATE_CHANGE
    }

    @Synchronized
    fun addEvent(event: TemporalEvent) {
        pruneOldEvents()
        events.add(event)
        if (events.size > maxBufferSize) {
            events.removeAt(0)
        }
    }

    @Synchronized
    fun getRecentEvents(withinMs: Long = maxEventAgeMs): List<TemporalEvent> {
        pruneOldEvents()
        val cutoff = System.currentTimeMillis() - withinMs
        return events.filter { it.timestamp >= cutoff }.toList()
    }

    /**
     * Calculate temporal persistence: how consistently anomalous events
     * have been arriving over a time window.
     */
    @Synchronized
    fun calculatePersistence(windowMs: Long = 10_000L): Float {
        val cutoff = System.currentTimeMillis() - windowMs
        val recentEvents = events.filter { it.timestamp >= cutoff }

        if (recentEvents.isEmpty()) return 0f

        val anomalyEvents = recentEvents.filter {
            it.type != EventType.STATE_CHANGE && it.score > 0.4f
        }

        if (anomalyEvents.isEmpty()) return 0f

        // Persistence = ratio of anomalous events × average score
        val ratio = anomalyEvents.size.toFloat() / recentEvents.size.coerceAtLeast(1)
        val avgScore = anomalyEvents.map { it.score }.average().toFloat()

        return (ratio * avgScore).coerceIn(0f, 1f)
    }

    /**
     * Check if a duplicate event was already added within a suppression window.
     */
    @Synchronized
    fun isDuplicate(type: EventType, suppressionWindowMs: Long): Boolean {
        val cutoff = System.currentTimeMillis() - suppressionWindowMs
        return events.any { it.type == type && it.timestamp >= cutoff }
    }

    /**
     * Count corroborating sensor types within a time window.
     */
    @Synchronized
    fun getCorroboratingSensorCount(windowMs: Long = 15_000L): Int {
        val cutoff = System.currentTimeMillis() - windowMs
        val recentTypes = events
            .filter { it.timestamp >= cutoff && it.score > 0.4f }
            .map { it.type }
            .toSet()

        return listOf(
            EventType.MOTION_ANOMALY,
            EventType.AUDIO_EVENT,
            EventType.VISION_EVENT
        ).count { it in recentTypes }
    }

    @Synchronized
    fun clear() {
        events.clear()
    }

    @Synchronized
    fun size(): Int {
        pruneOldEvents()
        return events.size
    }

    private fun pruneOldEvents() {
        val cutoff = System.currentTimeMillis() - maxEventAgeMs
        events.removeAll { it.timestamp < cutoff }
    }
}
