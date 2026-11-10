package com.rehabtrainer.app.data.session

import com.rehabtrainer.app.data.contract.Dosage
import com.rehabtrainer.app.data.contract.Exercise
import com.rehabtrainer.app.data.contract.ExerciseCategory
import com.rehabtrainer.app.data.contract.ExerciseVariant
import com.rehabtrainer.app.data.contract.Prescription
import com.rehabtrainer.app.data.contract.PrescriptionItem
import com.rehabtrainer.app.data.contract.PrescriptionStatus
import com.rehabtrainer.app.data.contract.PublicationStatus
import com.rehabtrainer.app.data.contract.SupportLevel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSelectorTest {

    private fun prescription(
        id: String,
        status: PrescriptionStatus,
        issuedAt: String = "2026-11-01T08:00:00",
        validFrom: String? = null,
        validUntil: String? = "2026-12-01T08:00:00",
        items: List<PrescriptionItem> = listOf(item("ex-gait-01"))
    ) = Prescription(
        id = id,
        patientId = "patient-a",
        prescribedBy = "therapist-a",
        items = items,
        issuedAt = issuedAt,
        version = "1.0.0",
        status = status,
        validFrom = validFrom,
        validUntil = validUntil
    )

    private fun item(code: String, dosage: Dosage? = null) =
        PrescriptionItem(exerciseCode = code, dosage = dosage)

    private fun exercise(
        code: String,
        category: ExerciseCategory = ExerciseCategory.RHYTHMIC_GAIT,
        videoUri: String? = null
    ) = Exercise(
        code = code,
        label = "训练 $code",
        category = category,
        publicationStatus = PublicationStatus.PUBLISHED,
        defaultDosage = Dosage(sets = 3, repetitions = 12, bpm = 100, restSeconds = 30),
        videoUri = videoUri,
        videoCreditLine = "署名",
        videoWatermarkText = "水印"
    )

    @Test
    fun selectsActiveUnexpiredPrescription() {
        val current = prescription("rx-new", PrescriptionStatus.ACTIVE, contentHash())
        val expired = prescription("rx-old", PrescriptionStatus.ACTIVE, validUntil = "2026-01-01T00:00:00")
        val selected = PlanSelector.selectCurrent(listOf(expired, current), "2026-11-06T10:00:00")
        assertEquals("rx-new", selected?.id)
    }

    @Test
    fun ignoresNotYetEffectivePrescription() {
        // 尚未到 validFrom 的处方不能当作当前方案，否则患者会提前开始训练。
        val future = prescription(
            "rx-future",
            PrescriptionStatus.ACTIVE,
            issuedAt = "2026-11-10T08:00:00",
            validFrom = "2026-12-01T00:00:00"
        )
        val current = prescription("rx-now", PrescriptionStatus.ACTIVE)
        val selected = PlanSelector.selectCurrent(
            listOf(future, current),
            "2026-11-06T10:00:00"
        )
        assertEquals("rx-now", selected?.id)
    }

    @Test
    fun returnsNullWhenAllExpired() {
        // 过期处方不再静默回退：按失效方案训练会生成错误记录，界面应提示联系治疗师。
        val expired = prescription("rx-old", PrescriptionStatus.ACTIVE, validUntil = "2026-01-01T00:00:00")
        val draft = prescription("rx-draft", PrescriptionStatus.DRAFT)
        assertNull(PlanSelector.selectCurrent(listOf(draft, expired), "2026-11-06T10:00:00"))
    }

    @Test
    fun mergeUsesVariantDosageAndSupportLevel() {
        val variant = ExerciseVariant(
            code = "seat",
            level = 1,
            supportLevel = SupportLevel.SEATED,
            defaultDosage = Dosage(sets = 1, repetitions = 5, bpm = 60, restSeconds = 60)
        )
        val exercise = exercise("ex-gait-01").copy(variants = listOf(variant))
        val rx = prescription(
            "rx-1",
            PrescriptionStatus.ACTIVE,
            items = listOf(PrescriptionItem(exerciseCode = "ex-gait-01", variantCode = "seat"))
        )
        val plan = PlanSelector.mergeItems(rx, mapOf("ex-gait-01" to exercise))

        assertEquals(1, plan[0].sets)
        assertEquals(5, plan[0].reps)
        assertEquals(60, plan[0].bpm)
        assertEquals("seated", plan[0].supportLevel)
    }

    @Test
    fun itemDosageOverridesVariantDefault() {
        val variant = ExerciseVariant(
            code = "seat",
            level = 1,
            supportLevel = SupportLevel.SEATED,
            defaultDosage = Dosage(sets = 1, repetitions = 5)
        )
        val exercise = exercise("ex-gait-01").copy(variants = listOf(variant))
        val rx = prescription(
            "rx-1",
            PrescriptionStatus.ACTIVE,
            items = listOf(
                PrescriptionItem(
                    exerciseCode = "ex-gait-01",
                    variantCode = "seat",
                    dosage = Dosage(repetitions = 8)
                )
            )
        )
        val plan = PlanSelector.mergeItems(rx, mapOf("ex-gait-01" to exercise))

        // 条目显式剂量优先于变体默认值，变体默认值优先于动作默认值。
        assertEquals(1, plan[0].sets)
        assertEquals(8, plan[0].reps)
    }

    @Test
    fun ignoresNonActivePrescriptions() {
        val draft = prescription("rx-draft", PrescriptionStatus.DRAFT)
        assertEquals(null, PlanSelector.selectCurrent(listOf(draft), "2026-11-06T10:00:00"))
    }

    @Test
    fun mergeUsesItemDosageOverDefault() {
        val rx = prescription(
            "rx-1",
            PrescriptionStatus.ACTIVE,
            items = listOf(item("ex-gait-01", Dosage(sets = 2, repetitions = 8, bpm = 90)))
        )
        val plan = PlanSelector.mergeItems(rx, mapOf("ex-gait-01" to exercise("ex-gait-01")))

        assertEquals(1, plan.size)
        assertEquals(2, plan[0].sets)
        assertEquals(8, plan[0].reps)
        assertEquals(90, plan[0].bpm)
    }

    @Test
    fun mergeFallsBackToExerciseDefaultAndSkipsMissing() {
        val rx = prescription(
            "rx-1",
            PrescriptionStatus.ACTIVE,
            items = listOf(item("ex-gait-01"), item("missing"))
        )
        val plan = PlanSelector.mergeItems(rx, mapOf("ex-gait-01" to exercise("ex-gait-01")))

        assertEquals(1, plan.size)
        assertEquals(3, plan[0].sets)
        assertEquals(12, plan[0].reps)
        assertEquals("水印", plan[0].watermarkText)
        assertEquals("署名", plan[0].creditLine)
    }

    @Test
    fun categoryDrivesRasAndFullBody() {
        assertTrue(PlanSelector.hasRas("rhythmic-gait"))
        assertFalse(PlanSelector.hasRas("stretching-relaxation"))
        assertFalse(PlanSelector.requiresFullBody("stretching-relaxation"))
        assertTrue(PlanSelector.requiresFullBody("rhythmic-gait"))
    }

    @Test
    fun snapshotRoundTrips() {
        val snapshot = SessionSnapshot(
            accountId = "account-1",
            displayName = "张三",
            orgName = "康复中心",
            patientId = "patient-1",
            patientName = "李四",
            prescriptionId = "rx-1",
            prescriptionVersion = "1.0.1",
            plan = listOf(
                PlanExercise(
                    code = "ex-gait-01",
                    label = "原地踏步",
                    category = "rhythmic-gait",
                    sets = 3,
                    reps = 12,
                    bpm = 100,
                    restSeconds = 30,
                    videoUri = "http://127.0.0.1:8000/media/videos/a.mp4"
                )
            ),
            loadedAtEpochMillis = 1_700_000_000_000L
        )
        val json = Json { encodeDefaults = true }
        val decoded = json.decodeFromString<SessionSnapshot>(json.encodeToString(snapshot))
        assertEquals(snapshot, decoded)
    }

    private fun contentHash(): String = "2026-11-05T08:00:00"
}