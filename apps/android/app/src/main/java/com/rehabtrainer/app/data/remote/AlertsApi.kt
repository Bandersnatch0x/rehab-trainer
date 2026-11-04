package com.rehabtrainer.app.data.remote

import com.rehabtrainer.app.data.contract.Alert
import com.rehabtrainer.app.data.contract.AlertLevel
import com.rehabtrainer.app.data.contract.AlertPage
import com.rehabtrainer.app.data.contract.AlertStatus
import com.rehabtrainer.app.data.contract.AlertUpdateRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface AlertsApi {

    /** GET /alerts — 查询预警事件。 */
    @GET("alerts")
    suspend fun listAlerts(
        @Query("patientId") patientId: String? = null,
        @Query("level") level: AlertLevel? = null,
        @Query("status") status: AlertStatus? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): AlertPage

    /** PATCH /alerts/{alertId} — 标记预警处理结果（仅照护管理员与康复治疗师）。 */
    @PATCH("alerts/{alertId}")
    suspend fun updateAlert(
        @Path("alertId") alertId: String,
        @Body body: AlertUpdateRequest
    ): Alert
}
