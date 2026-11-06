package com.rehabtrainer.app.data.session

import android.content.Context
import com.rehabtrainer.app.core.network.ApiContainer
import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.core.network.getOrNull
import com.rehabtrainer.app.data.contract.ApiError
import com.rehabtrainer.app.data.contract.Exercise
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * 设备会话：绑定/登录 → 拉取账号、患者、当前训练方案与条目详情 → 本地快照。
 *
 * 设计约束：
 * - 只读，不改任何业务数据；写操作仍走各自接口。
 * - 快照落 SharedPreferences，断网启动可直接用上一次成功的方案。
 * - 令牌由 [ApiContainer.tokenStore] 持有（当前为 SharedPreferences 实现）。
 */
class SessionRepository(
    private val api: ApiContainer,
    private val context: Context,
    private val nowIsoPrefix: () -> String = { Instant.now().toString().take(19) }
) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun hasSession(): Boolean = api.tokenStore.current() != null

    fun cachedSnapshot(): SessionSnapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return runCatching { json.decodeFromString<SessionSnapshot>(raw) }.getOrNull()
    }

    fun logout() {
        api.clearSession()
        prefs.edit().clear().apply()
    }

    suspend fun bind(bindingCode: String, deviceId: String?): ApiResult<SessionSnapshot> {
        val result = api.bind(bindingCode, deviceId)
        return when (result) {
            is ApiResult.Success -> loadSnapshot()
            is ApiResult.Failure -> result
        }
    }

    suspend fun login(username: String, password: String, totp: String?): ApiResult<SessionSnapshot> {
        val result = api.login(username, password, totp)
        return when (result) {
            is ApiResult.Success -> loadSnapshot()
            is ApiResult.Failure -> result
        }
    }

    /** 重新拉取快照；调用方负责在失败时回退到 [cachedSnapshot]。 */
    suspend fun refresh(): ApiResult<SessionSnapshot> = loadSnapshot()

    private suspend fun loadSnapshot(): ApiResult<SessionSnapshot> {
        val me = api.me().getOrNull()
            ?: return ApiResult.Failure(ApiError("UNAUTHORIZED", "登录状态已失效，请重新绑定设备"))
        val patients = api.listPatients(limit = 5).getOrNull()?.items.orEmpty()
        val patient = patients.firstOrNull()
            ?: return ApiResult.Failure(
                ApiError("NO_PATIENT", "当前账号未绑定患者，请联系机构获取绑定码")
            )

        val prescriptions = api.listPrescriptions(patient.id, limit = 20).getOrNull()?.items.orEmpty()
        val current = PlanSelector.selectCurrent(prescriptions, nowIsoPrefix())

        val exercises = mutableMapOf<String, Exercise>()
        current?.items.orEmpty().forEach { item ->
            api.getExercise(item.exerciseCode).getOrNull()?.let { exercises[it.code] = it }
        }

        val snapshot = SessionSnapshot(
            accountId = me.accountId,
            displayName = me.displayName,
            orgName = me.orgName,
            patientId = patient.id,
            patientName = patient.displayName,
            prescriptionId = current?.id,
            prescriptionVersion = current?.version,
            plan = PlanSelector.mergeItems(current, exercises),
            loadedAtEpochMillis = System.currentTimeMillis()
        )
        prefs.edit().putString(KEY_SNAPSHOT, json.encodeToString(snapshot)).apply()
        return ApiResult.Success(snapshot)
    }

    private companion object {
        const val PREFS_NAME = "rehab_session_snapshot"
        const val KEY_SNAPSHOT = "snapshot"
    }
}