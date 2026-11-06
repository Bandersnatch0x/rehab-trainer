package com.rehabtrainer.app.core.network

import com.rehabtrainer.app.data.contract.ApiError
import com.rehabtrainer.app.data.contract.BindRequest
import com.rehabtrainer.app.data.contract.Exercise
import com.rehabtrainer.app.data.contract.PatientPage
import com.rehabtrainer.app.data.contract.PrescriptionPage
import com.rehabtrainer.app.data.contract.SessionRecordPage
import com.rehabtrainer.app.data.contract.LoginRequest
import com.rehabtrainer.app.data.contract.LoginResponse
import com.rehabtrainer.app.data.contract.Me
import com.rehabtrainer.app.data.contract.RefreshRequest
import com.rehabtrainer.app.data.contract.TokenPair
import com.rehabtrainer.app.data.remote.AccountApi
import com.rehabtrainer.app.data.remote.AlertsApi
import com.rehabtrainer.app.data.remote.AssessmentsApi
import com.rehabtrainer.app.data.remote.AuthApi
import com.rehabtrainer.app.data.remote.ExercisesApi
import com.rehabtrainer.app.data.remote.PatientsApi
import com.rehabtrainer.app.data.remote.PrescriptionsApi
import com.rehabtrainer.app.data.remote.SessionsApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

/**
 * 契约客户端的唯一组装点。
 *
 * 选型说明：Retrofit + kotlinx.serialization。
 * - Android 端契约以 JSON 为主，Retrofit 的声明式接口与 Retrofit 注解能逐条对上 OpenAPI 的
 *   path/method/query/header，便于对照审查。
 * - `converter-kotlinx-serialization` 为 Retrofit 官方 converter，和 Kotlin 数据类复用同一套
 *   `@Serializable` 类型，避免再引入 Moshi/Gson 的第二套模型。
 * - OkHttp 的 Interceptor + Authenticator 恰好覆盖「Bearer 注入」与「401 时调用 /auth/refresh」。
 *
 * 鉴权约定：
 * - [authApi] 由不带拦截器的客户端创建，只用于 `/auth/bind` 与 `/auth/refresh`。
 * - 其余 [patientsApi] 等由带 [AuthInterceptor] 与 [TokenAuthenticator] 的客户端创建。
 */
class ApiContainer(
    val tokenStore: TokenStore = InMemoryTokenStore(),
    baseUrl: String = ApiConfig.BASE_URL,
    httpClientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
) {

    private val apiBaseUrl: String = baseUrl

    init {
        require(apiBaseUrl.endsWith("/")) {
            "baseUrl 必须以 / 结尾，否则 Retrofit 会丢弃契约的 /v1 前缀"
        }
    }

    private val json: Json = ApiJson.instance
    private val converterFactory = json.asConverterFactory(ApiConfig.JSON_MEDIA_TYPE.toMediaType())

    private val authClient: OkHttpClient = httpClientBuilder.build()

    /** 不带 Bearer 的接口集合，仅供登录与刷新令牌使用。 */
    val authApi: AuthApi = buildRetrofit(authClient).create(AuthApi::class.java)

    private val authorizedClient: OkHttpClient = authClient.newBuilder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .authenticator(
            TokenAuthenticator(tokenStore) { refreshToken -> refreshBlocking(refreshToken) }
        )
        .build()

    private val authorizedRetrofit: Retrofit = buildRetrofit(authorizedClient)

    val patientsApi: PatientsApi = authorizedRetrofit.create(PatientsApi::class.java)
    val assessmentsApi: AssessmentsApi = authorizedRetrofit.create(AssessmentsApi::class.java)
    val exercisesApi: ExercisesApi = authorizedRetrofit.create(ExercisesApi::class.java)
    val prescriptionsApi: PrescriptionsApi = authorizedRetrofit.create(PrescriptionsApi::class.java)
    val sessionsApi: SessionsApi = authorizedRetrofit.create(SessionsApi::class.java)
    val alertsApi: AlertsApi = authorizedRetrofit.create(AlertsApi::class.java)
    val accountApi: AccountApi = authorizedRetrofit.create(AccountApi::class.java)

    /** POST /auth/bind；成功后立即写入 [TokenStore]。 */
    suspend fun bind(bindingToken: String, deviceId: String? = null): ApiResult<TokenPair> =
        apiCall {
            authApi.bind(BindRequest(bindingToken = bindingToken, deviceId = deviceId))
                .also(tokenStore::save)
        }

    /** POST /auth/login；成功后立即写入 [TokenStore]。 */
    suspend fun login(
        username: String,
        password: String,
        totp: String? = null
    ): ApiResult<LoginResponse> = apiCall {
        authApi.login(LoginRequest(username = username, password = password, totp = totp))
            .also {
                tokenStore.save(
                    TokenPair(
                        accessToken = it.accessToken,
                        refreshToken = it.refreshToken,
                        expiresIn = it.expiresIn
                    )
                )
            }
    }

    /** GET /me；需要已有有效令牌。 */
    suspend fun me(): ApiResult<Me> = apiCall { accountApi.me() }

    /** GET /patients — 当前角色可见的患者（患者角色为自己）。 */
    suspend fun listPatients(limit: Int = 20): ApiResult<PatientPage> =
        apiCall { patientsApi.listPatients(limit = limit) }

    /** GET /prescriptions — 按患者读取训练方案。 */
    suspend fun listPrescriptions(patientId: String, limit: Int = 20): ApiResult<PrescriptionPage> =
        apiCall { prescriptionsApi.listPrescriptions(patientId = patientId, limit = limit) }

    /** GET /exercises/{code} — 训练条目详情（含示范视频地址）。 */
    suspend fun getExercise(code: String): ApiResult<Exercise> =
        apiCall { exercisesApi.getExercise(code) }

    /** GET /sessions — 患者的训练记录。 */
    suspend fun listSessions(patientId: String, limit: Int = 20): ApiResult<SessionRecordPage> =
        apiCall { sessionsApi.listSessions(patientId = patientId, limit = limit) }

    /** POST /auth/refresh；使用当前 refreshToken 换新令牌。 */
    suspend fun refresh(): ApiResult<TokenPair> {
        val refreshToken = tokenStore.current()?.refreshToken
            ?: return ApiResult.Failure(
                ApiError(
                    code = ErrorCodes.UNAUTHORIZED,
                    message = "缺少 refreshToken，请重新绑定设备"
                )
            )
        return apiCall {
            authApi.refresh(RefreshRequest(refreshToken = refreshToken))
                .also(tokenStore::save)
        }
    }

    /** 登出或绑定失效时清空本地令牌。 */
    fun clearSession() {
        tokenStore.clear()
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(client)
            .addConverterFactory(converterFactory)
            .build()

    /**
     * OkHttp Authenticator 要求阻塞式刷新，因此这里用 runBlocking 包一层。
     * 只在收到 401 时由 OkHttp 工作线程调用，不占用主线程。
     */
    private fun refreshBlocking(refreshToken: String): TokenPair? = runBlocking {
        try {
            authApi.refresh(RefreshRequest(refreshToken = refreshToken))
                .also(tokenStore::save)
        } catch (e: HttpException) {
            if (e.code() == HTTP_UNAUTHORIZED) {
                // refreshToken 本身已失效，清空会话让上层引导重新绑定。
                tokenStore.clear()
            }
            null
        } catch (e: IOException) {
            // 网络暂不可用：保留令牌，等待下一次请求重试。
            null
        } catch (e: SerializationException) {
            null
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED: Int = 401
    }
}
