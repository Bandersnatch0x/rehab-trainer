package com.rehabtrainer.app.data.remote

import com.rehabtrainer.app.data.contract.Exercise
import com.rehabtrainer.app.data.contract.ExerciseCategory
import com.rehabtrainer.app.data.contract.ExercisePage
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ExercisesApi {

    /** GET /exercises — 列出训练条目。 */
    @GET("exercises")
    suspend fun listExercises(
        @Query("category") category: ExerciseCategory? = null,
        @Query("stage") stage: Int? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): ExercisePage

    /** GET /exercises/{code} — 获取训练条目详情。 */
    @GET("exercises/{code}")
    suspend fun getExercise(@Path("code") code: String): Exercise
}
