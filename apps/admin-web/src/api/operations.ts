import {
  API_BASE_URL,
  api,
  normalizeApiError,
  toResult,
  type ApiResult,
} from "./client";
import { getAccessToken, notifyUnauthorized } from "../auth/session";
import type {
  Alert,
  AlertListQuery,
  AlertPage,
  AlertUpdate,
  Assessment,
  AssessmentCreate,
  AssessmentPage,
  AuditEventPage,
  BindRequestBody,
  Exercise,
  ExerciseListQuery,
  ExercisePage,
  LoginRequest,
  LoginResponse,
  MediaAsset,
  MediaAssetListQuery,
  MediaAssetPage,
  MediaAssetPatch,
  Me,
  Patient,
  PatientCreate,
  PatientListQuery,
  PatientPage,
  Prescription,
  PrescriptionCreate,
  PrescriptionPage,
  PrescriptionPatch,
  PrescriptionListQuery,
  RefreshRequestBody,
  ResearchDataQuality,
  ResearchOverview,
  SessionBatchRequest,
  SessionBatchResult,
  SessionListQuery,
  SessionRecordPage,
  StageConfirmation,
  StageConfirmationCreate,
  TokenPair,
} from "./types";

/**
 * 契约 openapi.yaml 的全部操作，一一对应（当前 21 个）。
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

/** 6. POST /patients/{patientId}/stage-confirmations — 治疗师确认 H-Y 分级 */
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

/** 8. GET /patients/{patientId}/assessments — 列出评估与身体感受记录 */
export function listAssessments(
  patientId: string,
  query: { cursor?: string; limit?: number } = {},
): Promise<ApiResult<AssessmentPage>> {
  return toResult<AssessmentPage>(
    api.GET("/patients/{patientId}/assessments", {
      params: { path: { patientId }, query },
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

/** 9.5 GET /prescriptions — 按患者读处方（倒序，第一条为当前有效处方） */
export function listPrescriptions(
  query: PrescriptionListQuery,
): Promise<ApiResult<PrescriptionPage>> {
  return toResult<PrescriptionPage>(
    api.GET("/prescriptions", { params: { query } }),
  );
}

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

// ---------- audit ----------

/** 16. GET /audit-events — 查询本机构审计事件 */
export function listAuditEvents(
  query: {
    cursor?: string;
    limit?: number;
    action?: string;
    resourceType?: string;
    actorAccountId?: string;
    from?: string;
    to?: string;
  } = {},
): Promise<ApiResult<AuditEventPage>> {
  return toResult<AuditEventPage>(
    api.GET("/audit-events", { params: { query } }),
  );
}

// ---------- research ----------

/** 16. GET /research/overview — 本机构科研聚合画像（仅临床角色） */
export function getResearchOverview(): Promise<ApiResult<ResearchOverview>> {
  return toResult<ResearchOverview>(api.GET("/research/overview"));
}

/** 17. GET /research/data-quality — 本机构训练数据质量看板（仅临床角色） */
export function getResearchDataQuality(): Promise<
  ApiResult<ResearchDataQuality>
> {
  return toResult<ResearchDataQuality>(api.GET("/research/data-quality"));
}

// ---------- media ----------

/** 18. GET /media-assets — 列出本机构的视频资产 */
export function listMediaAssets(
  query: MediaAssetListQuery = {},
): Promise<ApiResult<MediaAssetPage>> {
  return toResult<MediaAssetPage>(
    api.GET("/media-assets", { params: { query } }),
  );
}

export interface MediaUploadMeta {
  exerciseCode?: string;
  applicableStages?: number[];
  watermarkText?: string;
  creditLine?: string;
  note?: string;
}

export interface UploadProgress {
  loaded: number;
  total: number;
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return undefined;
  }
}

/**
 * 19. POST /media-assets/upload — 上传 MP4（multipart，带上传进度）
 *
 * 走 XMLHttpRequest 而不是 openapi-fetch：上传进度只有 XHR 能拿到，
 * 否则大文件上传期间界面无法反馈。错误结构仍用同一套归一化。
 */
export function uploadMediaAsset(
  file: File,
  meta: MediaUploadMeta,
  onProgress?: (progress: UploadProgress) => void,
): Promise<ApiResult<MediaAsset>> {
  const form = new FormData();
  form.append("file", file, file.name);
  if (meta.exerciseCode) form.append("exerciseCode", meta.exerciseCode);
  if (meta.applicableStages?.length) {
    form.append("applicableStages", meta.applicableStages.join(","));
  }
  if (meta.watermarkText) form.append("watermarkText", meta.watermarkText);
  if (meta.creditLine) form.append("creditLine", meta.creditLine);
  if (meta.note) form.append("note", meta.note);

  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${API_BASE_URL}/media-assets/upload`);
    const token = getAccessToken();
    if (token) {
      xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    }
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress?.({ loaded: event.loaded, total: event.total });
      }
    };
    xhr.onload = () => {
      const payload = parseJson(xhr.responseText);
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve({ ok: true, data: payload as MediaAsset });
        return;
      }
      if (xhr.status === 401) {
        notifyUnauthorized();
      }
      resolve({ ok: false, error: normalizeApiError(payload, xhr.status) });
    };
    xhr.onerror = () => {
      resolve({
        ok: false,
        error: { code: "NETWORK_ERROR", message: "网络请求失败" },
      });
    };
    xhr.send(form);
  });
}

/** 20. PATCH /media-assets/{assetId} — 修改水印、署名、适用分级或上下架状态 */
export function updateMediaAsset(
  assetId: string,
  body: MediaAssetPatch,
): Promise<ApiResult<MediaAsset>> {
  return toResult<MediaAsset>(
    api.PATCH("/media-assets/{assetId}", {
      params: { path: { assetId } },
      body,
    }),
  );
}
