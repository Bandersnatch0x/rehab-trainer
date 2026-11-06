package com.rehabtrainer.app.ui

import com.rehabtrainer.app.core.pose.ExerciseKind
import com.rehabtrainer.app.data.session.PlanExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseRegistryTest {

    private fun plan(
        category: String,
        bpm: Int = 100,
        videoUri: String? = null,
        label: String = "原地节律踏步"
    ) = PlanExercise(
        code = "ex-1",
        label = label,
        category = category,
        sets = 3,
        reps = 12,
        bpm = bpm,
        restSeconds = 30,
        videoUri = videoUri,
        watermarkText = "水印",
        creditLine = "署名"
    )

    @Test
    fun mapsCategoriesToAnalyzers() {
        assertEquals(ExerciseKind.MARCH, kindFor("rhythmic-gait"))
        assertEquals(ExerciseKind.SQUAT, kindFor("strength-maintenance"))
        assertEquals(ExerciseKind.POSTURE, kindFor("postural-correction"))
        assertEquals(ExerciseKind.STRETCH, kindFor("stretching-relaxation"))
        assertEquals(
            ExerciseKind.SEATED_LEG_RAISE,
            kindFor("strength-maintenance", "坐位抬腿")
        )
        assertEquals(ExerciseKind.SQUAT, kindFor("strength-maintenance", "靠墙微蹲"))
    }

    @Test
    fun mapsPlanFieldsAndVideoMetadata() {
        val meta = toExerciseMeta(plan("rhythmic-gait", bpm = 96, videoUri = "http://host/a.mp4"))

        assertEquals("ex-1", meta.code)
        assertEquals(3, meta.sets)
        assertEquals(12, meta.repsPerSet)
        assertEquals(96, meta.bpm)
        assertTrue(meta.hasRas)
        assertTrue(meta.requiresFullBody)
        assertEquals("踏步次数", meta.counterLabel)
        assertEquals("http://host/a.mp4", meta.videoUri)
        assertEquals("水印", meta.watermarkText)
        assertEquals("署名", meta.creditLine)
    }

    @Test
    fun stretchingDoesNotRequireFullBodyOrRas() {
        val meta = toExerciseMeta(plan("stretching-relaxation", label = "肩颈牵伸"))
        assertFalse(meta.hasRas)
        assertFalse(meta.requiresFullBody)
        assertEquals(ExerciseKind.STRETCH, meta.kind)
    }

    @Test
    fun emptyPlanFallsBackToDemoExercises() {
        ExerciseRegistry.updateFromPlan(emptyList())
        assertEquals(DEFAULT_EXERCISES, ExerciseRegistry.exercises)
        ExerciseRegistry.reset()
    }
}