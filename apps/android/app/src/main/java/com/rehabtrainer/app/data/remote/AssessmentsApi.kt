package com.rehabtrainer.app.data.remote

import com.rehabtrainer.app.data.contract.Assessment
import com.rehabtrainer.app.data.contract.AssessmentCreate
import com.rehabtrainer.app.data.contract.StageConfirmation
import com.rehabtrainer.app.data.contract.StageConfirmationCreate
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface AssessmentsApi {

    /** POST /patients/{patientId}/stage-confirmations — 康复治疗师确认 H-Y 分级。 */
    @POST("patients/{patientId}/stage-confirmations")
    suspend fun confirmStage(
        @Path("patientId") patientId: String,
        @Body body: StageConfirmationCreate
    ): StageConfirmation

    /** POST /patients/{patientId}/assessments — 提交一次评估。 */
    @POST("patients/{patientId}/assessments")
    suspend fun submitAssessment(
        @Path("patientId") patientId: String,
        @Body body: AssessmentCreate
    ): Assessment
}
