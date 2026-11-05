package com.rehabtrainer.app.core.training

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingAccessPolicyTest {

    @Test
    fun unconfirmedStageBlocksTherapistClearanceExercise() {
        assertFalse(TrainingAccessPolicy.canStart(requiresStageConfirmation = true, stageConfirmed = false))
    }

    @Test
    fun confirmedStageAllowsTherapistClearanceExercise() {
        assertTrue(TrainingAccessPolicy.canStart(requiresStageConfirmation = true, stageConfirmed = true))
    }

    @Test
    fun exerciseWithoutClearanceRequirementAlwaysStarts() {
        assertTrue(TrainingAccessPolicy.canStart(requiresStageConfirmation = false, stageConfirmed = false))
    }
}
