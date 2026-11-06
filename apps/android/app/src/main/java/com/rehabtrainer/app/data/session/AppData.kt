package com.rehabtrainer.app.data.session

import android.content.Context
import com.rehabtrainer.app.core.network.ApiContainer
import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.data.contract.AlertPage
import com.rehabtrainer.app.data.contract.Patient
import com.rehabtrainer.app.data.contract.SessionRecordCreate
import com.rehabtrainer.app.data.contract.SessionRecordPage
import com.rehabtrainer.app.data.offline.ApiSessionSyncService
import com.rehabtrainer.app.data.offline.FileSessionUploadQueue
import com.rehabtrainer.app.data.offline.SessionSyncService
import com.rehabtrainer.app.data.offline.SessionUploadQueue
import java.io.File

/**
 * 应用数据层入口，启动时由 `MainActivity` [install] 一次。两类职责：
 *
 * - 写：训练记录入队 → 立即尝试补传 → 失败留在本地等待下次（离线可用）。
 * - 读：患者维度的服务端数据（训练记录 / 预警 / 患者详情）。
 *
 * 未安装（如测试、Compose 预览）时所有调用都是安全的空操作（读返回 null，写返回 false）。
 */
object AppData {

    @Volatile
    private var queue: SessionUploadQueue? = null

    @Volatile
    private var sync: SessionSyncService? = null

    @Volatile
    private var api: ApiContainer? = null

    fun install(context: Context, container: ApiContainer) {
        val dir = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
        val uploadQueue = FileSessionUploadQueue(dir)
        queue = uploadQueue
        api = container
        sync = ApiSessionSyncService(uploadQueue) { container.sessionsApi.uploadSessions(it) }
    }

    /** 入队并尽力上传；返回是否成功入队（不代表已上传成功）。 */
    suspend fun submit(record: SessionRecordCreate): Boolean {
        val uploadQueue = queue ?: return false
        if (!uploadQueue.enqueue(record)) {
            return false
        }
        runCatching { sync?.flushPending() }
        return true
    }

    /** 应用回到前台/启动时调用，补传离线期间积压的记录。 */
    suspend fun flushPending() {
        runCatching { sync?.flushPending() }
    }

    suspend fun pendingCount(): Int = queue?.pendingCount() ?: 0

    suspend fun listSessions(patientId: String, limit: Int = 20): ApiResult<SessionRecordPage>? =
        api?.listSessions(patientId, limit)

    suspend fun listAlerts(patientId: String, limit: Int = 20): ApiResult<AlertPage>? =
        api?.listAlerts(patientId, limit = limit)

    suspend fun getPatient(patientId: String): ApiResult<Patient>? = api?.getPatient(patientId)

    private const val DIR_NAME = "session-uploads"
}