package com.rehabtrainer.app.core.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetProgressTest {

    @Test
    fun startsAtFirstSet() {
        val progress = SetProgress(totalSets = 3, repsPerSet = 12)

        assertEquals(1, progress.currentSet)
        assertEquals(3, progress.totalSets)
        assertFalse(progress.isComplete)
    }

    @Test
    fun repsAreCappedAtTarget() {
        val progress = SetProgress(totalSets = 3, repsPerSet = 12)

        assertEquals(12, progress.capReps(19))
        assertEquals(0, progress.capReps(-1))
        assertEquals(7, progress.capReps(7))
    }

    @Test
    fun finishingSetAdvancesGroupNumber() {
        val progress = SetProgress(totalSets = 3, repsPerSet = 12)

        val second = progress.finishCurrentSet()
        assertEquals(2, second.currentSet)
        assertFalse(second.isComplete)

        val third = second.finishCurrentSet()
        assertEquals(3, third.currentSet)
        assertFalse(third.isComplete)
    }

    @Test
    fun finishingLastSetCompletesTraining() {
        val progress = SetProgress(totalSets = 3, repsPerSet = 12, completedSets = 2)

        val complete = progress.finishCurrentSet()

        assertTrue(complete.isComplete)
        assertEquals(3, complete.currentSet)
    }

    @Test
    fun finishingCompletedTrainingDoesNotOverflow() {
        val progress = SetProgress(totalSets = 3, repsPerSet = 12, completedSets = 3)

        val again = progress.finishCurrentSet()

        assertTrue(again.isComplete)
        assertEquals(3, again.completedSets)
    }
}
