package com.rehabtrainer.app.data.session

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    /** 当前登录账号；队列上传只取该账号的条目。null 表示未登录/演示模式。 */
    @Volatile
    private var ownerAccountId: String? = null

    // 训练页退出后仍要把记录写完并补传；不能挂在 Composable 的 rememberCoroutineScope 上。
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun install(context: Context, container: ApiContainer) {
        // Activity 重建会再次调用 install；队列必须保持单实例，否则两个 Mutex
        // 会并发改写同一个 session-uploads.json，静默丢失待上传记录。
        if (queue != null) return
        val dir = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
        val uploadQueue = FileSessionUploadQueue(dir)
        queue = uploadQueue
        api = container
        sync = ApiSessionSyncService(uploadQueue) { container.sessionsApi.uploadSessions(it) }
    }

    /** 入队并尽力上传；返回是否成功入队（不代表已上传成功）。 */
    suspend fun submit(record: SessionRecordCreate): Boolean {
        val uploadQueue = queue ?: return false
        // 未登录/演示模式不落真实记录：没有归属的条目既不能上传，也无从人工处置。
        val owner = ownerAccountId ?: return false
        val enqueued = try {
            // 归属随记录落盘：登出/换绑后，旧账号的记录不会被新令牌带上去。
            uploadQueue.enqueue(record, owner)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 磁盘写失败不能把训练流程带崩；记录未入队，返回 false 由调用方决定提示。
            Log.w(TAG, "enqueue session record failed", e)
            return false
        }
        if (!enqueued) {
            return false
        }
        flushQuietly()
        return true
    }

    /** 登录/刷新成功后记录当前账号；队列上传只取该账号的条目。 */
    fun setOwner(accountId: String?) {
        ownerAccountId = accountId
    }

    /**
     * 登出/换绑：先同步摘掉归属，再异步清掉上一账号的队列。
     *
     * 即便清理还没跑完新账号就登录，[flushQuietly] 也只会取新账号的条目，
     * 旧患者的记录不会带着新令牌上传。
     */
    fun onLogout(previousOwnerAccountId: String?) {
        ownerAccountId = null
        appScope.launch {
            try {
                queue?.clearByOwner(previousOwnerAccountId)
                // 归属未知的遗留条目同样属于本机旧会话，登出时一并清理。
                queue?.clearByOwner(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "clear pending sessions failed", e)
            }
        }
    }

    /** 与 submit 相同，但使用应用级作用域，调用方退出组合不会取消写入。 */
    fun submitDetached(record: SessionRecordCreate) {
        appScope.launch { submit(record) }
    }

    /** 应用回到前台/启动时调用，补传离线期间积压的记录。 */
    suspend fun flushPending() {
        flushQuietly()
    }

    private suspend fun flushQuietly() {
        val owner = ownerAccountId ?: return
        try {
            sync?.flushPending(ownerAccountId = owner)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "flush pending sessions failed", e)
        }
    }

    suspend fun pendingCount(): Int = queue?.pendingCount() ?: 0

    /** 需人工处置的条目数（当前账号被服务端拒绝的记录）。 */
    suspend fun heldCount(): Int {
        val owner = ownerAccountId ?: return 0
        return queue?.heldCount(owner) ?: 0
    }

    /** 人工重试：把隔离/归属不明的记录归还当前账号并立即补传。 */
    suspend fun retryHeld() {
        val owner = ownerAccountId ?: return
        try {
            queue?.retryHeld(owner)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "retry held sessions failed", e)
            return
        }
        flushQuietly()
    }

    suspend fun listSessions(patientId: String, limit: Int = 20): ApiResult<SessionRecordPage>? =
        api?.listSessions(patientId, limit)

    suspend fun listAlerts(patientId: String, limit: Int = 20): ApiResult<AlertPage>? =
        api?.listAlerts(patientId, limit = limit)

    suspend fun getPatient(patientId: String): ApiResult<Patient>? = api?.getPatient(patientId)

    private const val DIR_NAME = "session-uploads"
    private const val TAG = "AppData"
}