package com.rehabtrainer.app.core.contact

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TherapistDialerTest {

    @Test
    fun blankNumberReportsNotConfigured() {
        assertEquals(DialResult.NotConfigured, TherapistDialer.resolveNumber(null))
        assertEquals(DialResult.NotConfigured, TherapistDialer.resolveNumber(""))
        assertEquals(DialResult.NotConfigured, TherapistDialer.resolveNumber("   "))
    }

    @Test
    fun configuredNumberIsTrimmedAndReady() {
        val result = TherapistDialer.resolveNumber(" 021-1234-5678 ")
        assertTrue(result is DialResult.Ready)
        assertEquals("021-1234-5678", (result as DialResult.Ready).number)
    }
}
