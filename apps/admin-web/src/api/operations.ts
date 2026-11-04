import { api, toResult, type ApiResult } from "./client";
import type {
  Alert,
  AlertListQuery,
  AlertPage,
  AlertUpdate,
  Assessment,
  AssessmentCreate,
  BindRequestBody,
  Exercise,
  ExerciseListQuery,
  ExercisePage,
  LoginRequest,
  LoginResponse,
  Me,
  Patient,
  PatientCreate,
  PatientListQuery,
  PatientPage,
  Prescription,
  PrescriptionCreate,
  PrescriptionPatch,
  RefreshRequestBody,
  SessionBatchRequest,
  SessionBatchResult,
  SessionListQuery,
  SessionRecordPage,
  StageConfirmation,
  StageConfirmationCreate,
  TokenPair,
} from "./types";

/**
 * 契约 openapi.yaml 的 15 个操作，一一对应。
 * 页面只调用本文件，不直接接触 openapi-fetch 的原始返回结构。
 */

// ---------- auth ----------

/** POST /auth/login — 内部账号登录，令牌与账号档案一次返回 */
export function login(
  body: LoginRequest,
): Promise<ApiResult<LoginResponse>> {
  return toResult<LoginResponse>(api.POST("/auth/login", { body }));
}

/** GET /me — 当前账号信息 */
export function getMe(): Promise<ApiResult<Me>> {
  return toResult<Me>(api.GET("/me"));
}

/** 1. POST /auth/bind — 用一次性绑定码换取访问令牌 */
export function bindWithCode(
  body: BindRequestBody,
): Promise<ApiResult<TokenPair>> {
  return toResult<TokenPair>(api.POST("/auth/bind", { body }));
}

/** 2. POST /auth/refresh — 刷新访问令牌 */
export function refreshTokens(
  body: RefreshRequestBody,
): Promise<ApiResult<TokenPair>> {
  return toResult<TokenPair>(api.POST("/auth/refresh", { body }));
}

// ---------- patients ----------

/** 3. GET /patients — 列出当前机构下的患者 */
export function listPatients(
  query: PatientListQuery = {},
): Promise<ApiResult<PatientPage>> {
  return toResult<PatientPage>(api.GET("/patients", { params: { query } }));
}

/** 4. POST /patients — 新建患者档案 */
export function createPatient(
  body: PatientCreate,
): Promise<ApiResult<Patient>> {
  return toResult<Patient>(api.POST("/patients", { body }));
}

/** 5. GET /patients/{patientId} — 获取患者详情 */
export function getPatient(patientId: string): Promise<ApiResult<Patient>> {
  return toResult<Patient>(
    api.GET("/patients/{patientId}", { params: { path: { patientId } } }),
  );
}

// ---------- assessments ----------

/** 6. POST /patients/{patientId}/stage-confirmations — 治疗师确认 H-Y 分期 */
export function confirmStage(
  patientId: string,
  body: StageConfirmationCreate,
): Promise<ApiResult<StageConfirmation>> {
  return toResult<StageConfirmation>(
    api.POST("/patients/{patientId}/stage-confirmations", {
      params: { path: { patientId } },
      body,
    }),
  );
}

/** 7. POST /patients/{patientId}/assessments — 提交一次评估 */
export function createAssessment(
  patientId: string,
  body: AssessmentCreate,
): Promise<ApiResult<Assessment>> {
  return toResult<Assessment>(
    api.POST("/patients/{patientId}/assessments", {
      params: { path: { patientId } },
      body,
    }),
  );
}

// ---------- exercises ----------

/** 8. GET /exercises — 列出训练条目 */
export function listExercises(
  query: ExerciseListQuery = {},
): Promise<ApiResult<ExercisePage>> {
  return toResult<ExercisePage>(api.GET("/exercises", { params: { query } }));
}

/** 9. GET /exercises/{code} — 获取训练条目详情 */
export function getExercise(code: string): Promise<ApiResult<Exercise>> {
  return toResult<Exercise>(
    api.GET("/exercises/{code}", { params: { path: { code } } }),
  );
}

// ---------- prescriptions ----------

/** 10. POST /prescriptions — 开具处方 */
export function createPrescription(
  body: PrescriptionCreate,
): Promise<ApiResult<Prescription>> {
  return toResult<Prescription>(api.POST("/prescriptions", { body }));
}

/** 11. PATCH /prescriptions/{prescriptionId} — 调整处方（If-Match 乐观锁） */
export function updatePrescription(
  prescriptionId: string,
  body: PrescriptionPatch,
  ifMatch: string,
): Promise<ApiResult<Prescription>> {
  return toResult<Prescription>(
    api.PATCH("/prescriptions/{prescriptionId}", {
      params: { path: { prescriptionId }, header: { "If-Match": ifMatch } },
      body,
    }),
  );
}

// ---------- sessions ----------

/** 12. POST /sessions — 批量上传训练记录（clientRecordId 幂等） */
export function uploadSessions(
  body: SessionBatchRequest,
): Promise<ApiResult<SessionBatchResult>> {
  return toResult<SessionBatchResult>(api.POST("/sessions", { body }));
}

/** 13. GET /sessions — 查询训练记录 */
export function listSessions(
  query: SessionListQuery,
): Promise<ApiResult<SessionRecordPage>> {
  return toResult<SessionRecordPage>(
    api.GET("/sessions", { params: { query } }),
  );
}

// ---------- alerts ----------

/** 14. GET /alerts — 查询预警事件 */
export function listAlerts(
  query: AlertListQuery = {},
): Promise<ApiResult<AlertPage>> {
  return toResult<AlertPage>(api.GET("/alerts", { params: { query } }));
}

/** 15. PATCH /alerts/{alertId} — 标记预警处理结果 */
export function updateAlert(
  alertId: string,
  body: AlertUpdate,
): Promise<ApiResult<Alert>> {
  return toResult<Alert>(
    api.PATCH("/alerts/{alertId}", {
      params: { path: { alertId } },
      body,
    }),
  );
}
