package com.rehabtrainer.app.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 按 患者 + 训练条目 存取个人动作幅度。
 *
 * 为什么需要它：幅度校准原本每次训练重来（前 2 次动作只测量不计数）。存下上次的幅度后，
 * 第二次训练起第一次动作就计入，也避免患者因"动作做了却没计数"而困惑。
 *
 * 为什么带有效期：[TTL_MILLIS] 过期即当作没有，重新校准。康复过程中幅度会随状态变化，
 * 长期沿用旧幅度会让阈值过松（多计）或过紧（漏计）。
 *
 * 文件实现，写入走「临时文件 + rename」，损坏时按空处理，与本项目其它本地队列一致。
 */
class CalibrationStore(
    root: File,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val file = File(root, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 未存过、已过期或文件损坏时返回 null，由分析器重新校准。 */
    fun amplitude(patientId: String, exerciseCode: String): Float? {
        val entry = load()[key(patientId, exerciseCode)] ?: return null
        if (clock() - entry.updatedAt > TTL_MILLIS) return null
        return entry.amplitude.takeIf { it.isFinite() && it > 0f }
    }

    fun save(patientId: String, exerciseCode: String, amplitude: Float) {
        if (!amplitude.isFinite() || amplitude <= 0f) return
        val all = load().toMutableMap()
        all[key(patientId, exerciseCode)] = Entry(amplitude = amplitude, updatedAt = clock())
        persist(all)
    }

    /**
     * 丢弃某条目的幅度。用于「存下的幅度明显不合身」的情形：整组下来识别到了人却一次都没计数，
     * 继续沿用只会一直不计数，不如退回重新校准。
     */
    fun invalidate(patientId: String, exerciseCode: String) {
        val all = load().toMutableMap()
        if (all.remove(key(patientId, exerciseCode)) != null) {
            persist(all)
        }
    }

    private fun key(patientId: String, exerciseCode: String) = "$patientId|$exerciseCode"

    private fun load(): Map<String, Entry> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            json.decodeFromString<Stored>(file.readText()).entries
        }.getOrDefault(emptyMap())
    }

    private fun persist(entries: Map<String, Entry>) {
        // 校准缓存是尽力而为的本地状态：写盘失败（磁盘满、文件被占用）不应中断训练流程。
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(json.encodeToString(Stored(entries)))
            if (!temp.renameTo(file)) {
                // Windows 上 rename 不能覆盖已存在的目标文件；只在这条兜底路径删旧文件。
                file.delete()
                check(temp.renameTo(file)) {
                    "幅度校准文件原子替换失败：${file.absolutePath}"
                }
            }
        }
    }

    @Serializable
    private data class Entry(val amplitude: Float, val updatedAt: Long)

    @Serializable
    private data class Stored(val entries: Map<String, Entry> = emptyMap())

    companion object {
        const val FILE_NAME: String = "amplitude-calibration.json"

        /** 30 天：够跨过常规复诊间隔，又不至于让旧幅度长期生效。 */
        const val TTL_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

        /** 判「这一组被有效观察过」的最小帧数：约几秒稳定识别。 */
        const val MIN_VALID_FRAMES_TO_JUDGE: Int = 45

        /**
         * 是否应丢弃存下的幅度。
         *
         * 判据是「整组明明识别到了人却一次都没计数」——存下的幅度相对当前状态过紧，
         * 继续沿用只会一直不计数。有效帧太少时说明这一组根本没被观察到，不能据此否定它。
         */
        fun shouldInvalidate(
            knownAmplitude: Float?,
            completedReps: Int,
            validFrames: Int
        ): Boolean = knownAmplitude != null &&
            completedReps <= 0 &&
            validFrames >= MIN_VALID_FRAMES_TO_JUDGE
    }
}
