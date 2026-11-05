package com.rehabtrainer.app.core.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDisplayPolicyTest {

    private fun policy(
        mode: TrainingDisplayMode = TrainingDisplayMode.DUAL,
        isPhoneLandscape: Boolean = false,
        widthDp: Int = 360,
        voice: Boolean = true
    ) = TrainingDisplayPolicy(mode, isPhoneLandscape, widthDp, voice)

    @Test
    fun portraitDualShowsVideoWithoutSpeech() {
        val result = policy(mode = TrainingDisplayMode.DUAL, voice = true)

        assertTrue(result.showVideo)
        assertFalse(result.shouldSpeak)
        assertFalse(result.isForcedCameraOnly)
    }

    @Test
    fun cameraOnlyWithoutVoiceIsSilent() {
        val result = policy(mode = TrainingDisplayMode.CAMERA_ONLY, voice = false)

        assertFalse(result.showVideo)
        assertFalse(result.shouldSpeak)
    }

    @Test
    fun cameraOnlyWithVoiceSpeaks() {
        val result = policy(mode = TrainingDisplayMode.CAMERA_ONLY, voice = true)

        assertFalse(result.showVideo)
        assertTrue(result.shouldSpeak)
    }

    @Test
    fun narrowPhoneLandscapeForcesCameraOnly() {
        val result = policy(
            mode = TrainingDisplayMode.DUAL,
            isPhoneLandscape = true,
            widthDp = TrainingDisplayPolicy.MIN_LANDSCAPE_DUAL_WIDTH_DP - 1,
            voice = true
        )

        assertTrue(result.isForcedCameraOnly)
        assertEquals(TrainingDisplayMode.CAMERA_ONLY, result.effectiveMode)
        assertFalse(result.showVideo)
        assertTrue(result.shouldSpeak)
    }

    @Test
    fun widePhoneLandscapeKeepsSelectedMode() {
        val result = policy(
            mode = TrainingDisplayMode.DUAL,
            isPhoneLandscape = true,
            widthDp = TrainingDisplayPolicy.MIN_LANDSCAPE_DUAL_WIDTH_DP,
            voice = false
        )

        assertFalse(result.isForcedCameraOnly)
        assertTrue(result.showVideo)
    }
}
