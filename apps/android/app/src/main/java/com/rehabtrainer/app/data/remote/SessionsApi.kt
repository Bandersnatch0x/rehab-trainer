package com.rehabtrainer.app.data.remote

import com.rehabtrainer.app.data.contract.SessionBatchRequest
import com.rehabtrainer.app.data.contract.SessionBatchResult
import com.rehabtrainer.app.data.contract.SessionRecordPage
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SessionsApi {

    /**
     * POST /sessions — 批量上传训练记录。
     *
     * 服务端以 `clientRecordId` 去重；重复提交返回已存在记录的结果，调用方可安全重试。
     */
    @POST("sessions")
    suspend fun uploadSessions(@Body body: SessionBatchRequest): SessionBatchResult

    /** GET /sessions — 查询训练记录（`patientId` 必填）。 */
    @GET("sessions")
    suspend fun listSessions(
        @Query("patientId") patientId: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): SessionRecordPage
}
