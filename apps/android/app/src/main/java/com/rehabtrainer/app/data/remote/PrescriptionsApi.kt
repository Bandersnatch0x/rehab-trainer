package com.rehabtrainer.app.data.remote

import com.rehabtrainer.app.data.contract.Prescription
import com.rehabtrainer.app.data.contract.PrescriptionCreate
import com.rehabtrainer.app.data.contract.PrescriptionPatch
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface PrescriptionsApi {

    /** POST /prescriptions — 开具处方（仅康复治疗师）。 */
    @POST("prescriptions")
    suspend fun createPrescription(@Body body: PrescriptionCreate): Prescription

    /**
     * PATCH /prescriptions/{prescriptionId} — 调整处方。
     *
     * @param version 当前处方 version，作为 `If-Match` 请求头做乐观锁；冲突时服务端返回 409。
     */
    @PATCH("prescriptions/{prescriptionId}")
    suspend fun updatePrescription(
        @Path("prescriptionId") prescriptionId: String,
        @Header("If-Match") version: String,
        @Body body: PrescriptionPatch
    ): Prescription
}
