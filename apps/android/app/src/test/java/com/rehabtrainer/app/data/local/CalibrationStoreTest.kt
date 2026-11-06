package com.rehabtrainer.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CalibrationStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_700_000_000_000L

    private fun store() = CalibrationStore(folder.root, clock = { now })

    @Test
    fun `saved amplitude survives a new instance`() {
        store().save("patient-1", "EX-MARCH-01", 0.32f)

        // 新实例读同一目录：幅度按 患者+条目 记住。
        assertEquals(0.32f, store().amplitude("patient-1", "EX-MARCH-01")!!, 1e-6f)
    }

    @Test
    fun `other patient or other exercise does not inherit the amplitude`() {
        store().save("patient-1", "EX-MARCH-01", 0.32f)

        assertNull(store().amplitude("patient-2", "EX-MARCH-01"))
        assertNull(store().amplitude("patient-1", "EX-SQUAT-01"))
    }

    @Test
    fun `expired entry is ignored so the patient is recalibrated`() {
        store().save("patient-1", "EX-MARCH-01", 0.32f)

        now += CalibrationStore.TTL_MILLIS + 1

        // 幅度会随康复进展变化，过期后不能再拿来当阈值依据。
        assertNull(store().amplitude("patient-1", "EX-MARCH-01"))
    }

    @Test
    fun `entry just inside the ttl is still used`() {
        store().save("patient-1", "EX-MARCH-01", 0.32f)

        now += CalibrationStore.TTL_MILLIS - 1

        assertEquals(0.32f, store().amplitude("patient-1", "EX-MARCH-01")!!, 1e-6f)
    }

    @Test
    fun `invalidate drops only that entry`() {
        store().save("patient-1", "EX-MARCH-01", 0.32f)
        store().save("patient-1", "EX-SQUAT-01", 0.18f)

        store().invalidate("patient-1", "EX-MARCH-01")

        assertNull(store().amplitude("patient-1", "EX-MARCH-01"))
        assertEquals(0.18f, store().amplitude("patient-1", "EX-SQUAT-01")!!, 1e-6f)
    }

    @Test
    fun `nonsense amplitudes are not stored`() {
        val store = store()

        store.save("patient-1", "EX-MARCH-01", 0f)
        store.save("patient-1", "EX-MARCH-01", -1f)
        store.save("patient-1", "EX-MARCH-01", Float.NaN)

        assertNull(store.amplitude("patient-1", "EX-MARCH-01"))
    }

    @Test
    fun `corrupt file degrades to empty instead of crashing training`() {
        java.io.File(folder.root, CalibrationStore.FILE_NAME).writeText("{not json")

        assertNull(store().amplitude("patient-1", "EX-MARCH-01"))
        // 损坏后仍可写入：下一次训练照常校准并保存。
        store().save("patient-1", "EX-MARCH-01", 0.25f)
        assertEquals(0.25f, store().amplitude("patient-1", "EX-MARCH-01")!!, 1e-6f)
    }

    @Test
    fun `invalidate is a no-op when nothing was stored`() {
        // 未存过就整组零计数时不应写文件；这里只要求不抛异常、状态仍为空。
        store().invalidate("patient-1", "EX-MARCH-01")

        assertNull(store().amplitude("patient-1", "EX-MARCH-01"))
        org.junit.Assert.assertFalse(
            java.io.File(folder.root, CalibrationStore.FILE_NAME).exists()
        )
    }

    @Test
    fun `shouldInvalidate needs detection without any counted rep`() {
        val enough = CalibrationStore.MIN_VALID_FRAMES_TO_JUDGE

        // 沿用旧幅度、看清了人、却一次没计数 → 丢弃旧幅度重练。
        org.junit.Assert.assertTrue(CalibrationStore.shouldInvalidate(0.3f, 0, enough))
        // 没有任何已存幅度时无事可做。
        org.junit.Assert.assertFalse(CalibrationStore.shouldInvalidate(null, 0, enough))
        // 计数正常：幅度合身。
        org.junit.Assert.assertFalse(CalibrationStore.shouldInvalidate(0.3f, 5, enough))
        // 有效帧太少：这一组没被真正观察到，不能据此否定已存幅度。
        org.junit.Assert.assertFalse(CalibrationStore.shouldInvalidate(0.3f, 0, enough - 1))
    }
}
