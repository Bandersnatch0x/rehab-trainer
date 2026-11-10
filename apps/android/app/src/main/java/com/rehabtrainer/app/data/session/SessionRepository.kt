package com.rehabtrainer.app.data.session

import android.content.Context
import com.rehabtrainer.app.core.network.ApiContainer
import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.data.contract.ApiError
import com.rehabtrainer.app.data.contract.Exercise
import com.rehabtrainer.app.data.local.AppPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        // 待上传队列属于上一个患者，换绑后不能带着旧 token 重放或落到新账号名下。
        AppData.clearPending()
    }

    suspend fun bind(bindingCode: String, deviceId: String?): ApiResult<SessionSnapshot> {
        val result = api.bind(bindingCode, deviceId)
        return when (result) {
            is ApiResult.Success -> {
                AppPreferences.markDeviceBound(context)
                loadSnapshot()
            }

            is ApiResult.Failure -> result
        }
    }

    suspend fun login(username: String, password: String, totp: String?): ApiResult<SessionSnapshot> {
        val result = api.login(username, password, totp)
        return when (result) {
            is ApiResult.Success -> {
                AppPreferences.markDeviceBound(context)
                loadSnapshot()
            }

            is ApiResult.Failure -> result
        }
    }

    /** 重新拉取快照；调用方负责在失败时回退到 [cachedSnapshot]。 */
    suspend fun refresh(): ApiResult<SessionSnapshot> = loadSnapshot()

    private suspend fun loadSnapshot(): ApiResult<SessionSnapshot> {
        val me = when (val result = api.me()) {
            is ApiResult.Failure -> return if (result.httpStatus == 401) {
                ApiResult.Failure(ApiError("UNAUTHORIZED", "登录状态已失效，请重新绑定设备"))
            } else {
                // 超时/5xx/解析错误不是「会话失效」，原样上抛，避免错误提示误导用户。
                result
            }

            is ApiResult.Success -> result.value
        }

        val patients = when (val result = api.listPatients(limit = 5)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        // 治疗师/照护管理员可见多个患者；firstOrNull() 会把训练记录写到最老的无关患者名下。
        val patient = when {
            patients.items.isEmpty() -> return ApiResult.Failure(
                ApiError("NO_PATIENT", "当前账号未绑定患者，请联系机构获取绑定码")
            )
            patients.items.size > 1 -> return ApiResult.Failure(
                ApiError("MULTIPLE_PATIENTS", "当前账号可见多位患者，请使用绑定码指定患者")
            )
            else -> patients.items.first()
        }

        val prescriptions = when (val result = api.listPrescriptions(patient.id, limit = 20)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        val current = PlanSelector.selectCurrent(prescriptions.items, nowIsoPrefix())

        val exercises = mutableMapOf<String, Exercise>()
        val items = current?.items.orEmpty()
        // 逐条串行会在登录时叠加 N 次往返；并发拉取，任一详情失败则整体失败，避免半份方案落盘。
        val details = coroutineScope {
            items.map { item -> async { api.getExercise(item.exerciseCode) } }.awaitAll()
        }
        for (detail in details) {
            when (detail) {
                is ApiResult.Failure -> return detail
                is ApiResult.Success -> exercises[detail.value.code] = detail.value
            }
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