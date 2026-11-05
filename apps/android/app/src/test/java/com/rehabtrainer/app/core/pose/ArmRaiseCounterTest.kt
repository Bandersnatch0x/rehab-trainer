package com.rehabtrainer.app.core.pose

import org.junit.Assert.assertEquals
import org.junit.Test

class ArmRaiseCounterTest {

    @Test
    fun startsAtZero() {
        assertEquals(0, ArmRaiseCounter().count)
    }

    @Test
    fun raiseThenLowerCountsOnce() {
        val counter = ArmRaiseCounter()

        assertEquals(0, counter.update(-0.5f))
        assertEquals(1, counter.update(0.0f))
    }

    @Test
    fun jitterBetweenThresholdsDoesNotDoubleCount() {
        val counter = ArmRaiseCounter()

        counter.update(-0.5f)
        assertEquals(1, counter.update(-0.05f))
        assertEquals(1, counter.update(-0.2f))
        assertEquals(1, counter.update(0.1f))
    }

    @Test
    fun loweredArmWithoutRaisingDoesNotCount() {
        val counter = ArmRaiseCounter()

        counter.update(-0.2f)
        assertEquals(0, counter.update(0.1f))
    }

    @Test
    fun repeatedCyclesAccumulate() {
        val counter = ArmRaiseCounter()

        counter.update(-0.5f)
        counter.update(0.0f)
        counter.update(-0.5f)
        assertEquals(2, counter.update(0.0f))
    }

    @Test
    fun resetClearsCountAndRaisedState() {
        val counter = ArmRaiseCounter()
        counter.update(-0.5f)
        counter.update(0.0f)

        counter.reset()

        assertEquals(0, counter.count)
        assertEquals(0, counter.update(-0.5f))
        assertEquals(1, counter.update(0.0f))
    }
}
