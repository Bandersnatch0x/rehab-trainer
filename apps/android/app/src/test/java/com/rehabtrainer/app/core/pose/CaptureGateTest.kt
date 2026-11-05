package com.rehabtrainer.app.core.pose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureGateTest {

    @Test
    fun thresholdIsInclusive() {
        assertTrue(CaptureGate.isFootVisible(0.5f))
    }

    @Test
    fun belowThresholdIsNotVisible() {
        assertFalse(CaptureGate.isFootVisible(0.49f))
    }

    @Test
    fun fullVisibilityPasses() {
        assertTrue(CaptureGate.isFootVisible(1.0f))
    }

    @Test
    fun missingVisibilityFailsClosed() {
        assertFalse(CaptureGate.isFootVisible(0.0f))
        assertFalse(CaptureGate.isFootVisible(Float.NaN))
    }

    @Test
    fun upperBodyExerciseCountsWithoutFeet() {
        assertTrue(CaptureGate.shouldCount(requiresFullBody = false, footVisibility = 0f))
    }

    @Test
    fun fullBodyExerciseBlocksCountingWhenFeetHidden() {
        assertFalse(CaptureGate.shouldCount(requiresFullBody = true, footVisibility = 0.49f))
    }

    @Test
    fun fullBodyExerciseCountsWhenFeetVisible() {
        assertTrue(CaptureGate.shouldCount(requiresFullBody = true, footVisibility = 0.5f))
    }
}
