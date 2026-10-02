package com.safeguardian.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class SafetyStateTest {

    @Test
    fun `NORMAL can transition to ANOMALY_CANDIDATE`() {
        assertTrue(SafetyState.NORMAL.canTransitionTo(SafetyState.ANOMALY_CANDIDATE))
    }

    @Test
    fun `NORMAL cannot skip to INVESTIGATING`() {
        assertFalse(SafetyState.NORMAL.canTransitionTo(SafetyState.INVESTIGATING))
    }

    @Test
    fun `NORMAL cannot skip to ACTIVE_EMERGENCY`() {
        assertFalse(SafetyState.NORMAL.canTransitionTo(SafetyState.ACTIVE_EMERGENCY))
    }

    @Test
    fun `ANOMALY_CANDIDATE can transition to INVESTIGATING`() {
        assertTrue(SafetyState.ANOMALY_CANDIDATE.canTransitionTo(SafetyState.INVESTIGATING))
    }

    @Test
    fun `ANOMALY_CANDIDATE can de-escalate to NORMAL`() {
        assertTrue(SafetyState.ANOMALY_CANDIDATE.canTransitionTo(SafetyState.NORMAL))
    }

    @Test
    fun `INVESTIGATING can transition to SUSPICIOUS`() {
        assertTrue(SafetyState.INVESTIGATING.canTransitionTo(SafetyState.SUSPICIOUS))
    }

    @Test
    fun `SUSPICIOUS can escalate to HIGH_CONFIDENCE_EMERGENCY`() {
        assertTrue(SafetyState.SUSPICIOUS.canTransitionTo(SafetyState.HIGH_CONFIDENCE_EMERGENCY))
    }

    @Test
    fun `HIGH_CONFIDENCE_EMERGENCY can transition to ACTIVE_EMERGENCY`() {
        assertTrue(SafetyState.HIGH_CONFIDENCE_EMERGENCY.canTransitionTo(SafetyState.ACTIVE_EMERGENCY))
    }

    @Test
    fun `ACTIVE_EMERGENCY can be resolved`() {
        assertTrue(SafetyState.ACTIVE_EMERGENCY.canTransitionTo(SafetyState.RESOLVED))
    }

    @Test
    fun `RESOLVED can transition to NORMAL`() {
        assertTrue(SafetyState.RESOLVED.canTransitionTo(SafetyState.NORMAL))
    }

    @Test
    fun `RESOLVED cannot transition to ACTIVE_EMERGENCY`() {
        assertFalse(SafetyState.RESOLVED.canTransitionTo(SafetyState.ACTIVE_EMERGENCY))
    }
}
