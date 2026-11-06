package com.rehabtrainer.app.data.remote

import com.rehabtrainer.app.data.contract.Patient
import com.rehabtrainer.app.data.contract.PatientCreate
import com.rehabtrainer.app.data.contract.PatientPage
import com.rehabtrainer.app.data.contract.PatientStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PatientsApi {

    /** GET /patients — 列出当前机构下的患者。 */
    @GET("patients")
    suspend fun listPatients(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("stage") stage: Int? = null,
        @Query("status") status: PatientStatus? = null
    ): PatientPage

    /** POST /patients — 新建患者档案；新建后分级状态为 suspected。 */
    @POST("patients")
    suspend fun createPatient(@Body body: PatientCreate): Patient

    /** GET /patients/{patientId} — 获取患者详情。 */
    @GET("patients/{patientId}")
    suspend fun getPatient(@Path("patientId") patientId: String): Patient
}
