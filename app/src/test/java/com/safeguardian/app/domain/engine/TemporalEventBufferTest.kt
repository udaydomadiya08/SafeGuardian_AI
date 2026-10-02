package com.safeguardian.app.domain.engine

import com.safeguardian.app.domain.engine.TemporalEventBuffer.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TemporalEventBufferTest {

    private lateinit var buffer: TemporalEventBuffer

    @Before
    fun setup() {
        buffer = TemporalEventBuffer(maxBufferSize = 50, maxEventAgeMs = 10_000L)
    }

    @Test
    fun `empty buffer has zero size`() {
        assertEquals(0, buffer.size())
    }

    @Test
    fun `add event increases size`() {
        buffer.addEvent(TemporalEvent(EventType.MOTION_ANOMALY, 0.8f))
        assertEquals(1, buffer.size())
    }

    @Test
    fun `persistence is zero for empty buffer`() {
        assertEquals(0f, buffer.calculatePersistence(), 0.01f)
    }

    @Test
    fun `persistence increases with anomalous events`() {
        repeat(5) {
            buffer.addEvent(TemporalEvent(EventType.MOTION_ANOMALY, 0.8f))
        }
        val persistence = buffer.calculatePersistence()
        assertTrue("Persistence should be > 0", persistence > 0f)
    }

    @Test
    fun `duplicate detection works within window`() {
        buffer.addEvent(TemporalEvent(EventType.MOTION_ANOMALY, 0.8f))
        assertTrue(buffer.isDuplicate(EventType.MOTION_ANOMALY, 5_000L))
        assertFalse(buffer.isDuplicate(EventType.AUDIO_EVENT, 5_000L))
    }

    @Test
    fun `corroborating sensor count returns correct values`() {
        assertEquals(0, buffer.getCorroboratingSensorCount())

        buffer.addEvent(TemporalEvent(EventType.MOTION_ANOMALY, 0.8f))
        assertEquals(1, buffer.getCorroboratingSensorCount())

        buffer.addEvent(TemporalEvent(EventType.AUDIO_EVENT, 0.7f))
        assertEquals(2, buffer.getCorroboratingSensorCount())

        buffer.addEvent(TemporalEvent(EventType.VISION_EVENT, 0.6f))
        assertEquals(3, buffer.getCorroboratingSensorCount())
    }

    @Test
    fun `low-score events do not count as corroborating`() {
        buffer.addEvent(TemporalEvent(EventType.MOTION_ANOMALY, 0.2f))
        buffer.addEvent(TemporalEvent(EventType.AUDIO_EVENT, 0.1f))
        assertEquals(0, buffer.getCorroboratingSensorCount())
    }

    @Test
    fun `clear empties the buffer`() {
        buffer.addEvent(TemporalEvent(EventType.MOTION_ANOMALY, 0.8f))
        buffer.addEvent(TemporalEvent(EventType.AUDIO_EVENT, 0.7f))
        buffer.clear()
        assertEquals(0, buffer.size())
    }

    @Test
    fun `buffer respects max size`() {
        repeat(100) {
            buffer.addEvent(TemporalEvent(EventType.MOTION_ANOMALY, 0.5f))
        }
        assertTrue(buffer.size() <= 50)
    }
}
