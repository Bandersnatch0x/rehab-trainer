import type { components, paths } from "./schema";

/**
 * 契约类型别名。全部从 openapi-typescript 生成的 schema 派生，
 * 管理端不手写跨端字段（D15）。
 */

// ---------- 响应体 ----------
export type ApiContractError = components["schemas"]["Error"];
export type TokenPair = components["schemas"]["TokenPair"];
export type LoginRequest = components["schemas"]["LoginRequest"];
export type LoginResponse = components["schemas"]["LoginResponse"];
export type Me = components["schemas"]["Me"];

export type PatientStatus = components["schemas"]["PatientStatus"];
export type StageConfirmationStatus = components["schemas"]["StageConfirmationStatus"];
export type ExerciseCategory = components["schemas"]["ExerciseCategory"];
export type RiskLevel = components["schemas"]["RiskLevel"];
export type AlertLevel = components["schemas"]["AlertLevel"];

export type Patient = components["schemas"]["Patient"];
export type PatientPage = components["schemas"]["PatientPage"];
export type StageConfirmation = components["schemas"]["StageConfirmation"];
export type Assessment = components["schemas"]["Assessment"];
export type AssessmentPage = components["schemas"]["AssessmentPage"];
export type Exercise = components["schemas"]["Exercise"];
export type ExerciseVariant = components["schemas"]["ExerciseVariant"];
export type ExercisePage = components["schemas"]["ExercisePage"];
export type Dosage = components["schemas"]["Dosage"];
export type Prescription = components["schemas"]["Prescription"];
export type PrescriptionPage = components["schemas"]["PrescriptionPage"];
export type PrescriptionItem = components["schemas"]["PrescriptionItem"];
export type SessionRecord = components["schemas"]["SessionRecord"];
export type SessionRecordPage = components["schemas"]["SessionRecordPage"];
export type SessionBatchResult = components["schemas"]["SessionBatchResult"];
export type NotificationDelivery = components["schemas"]["NotificationDelivery"];
export type Alert = components["schemas"]["Alert"];
export type AlertPage = components["schemas"]["AlertPage"];
export type MediaAssetStatus = components["schemas"]["MediaAssetStatus"];
export type MediaAsset = components["schemas"]["MediaAsset"];
export type MediaAssetPage = components["schemas"]["MediaAssetPage"];
export type MediaAssetPatch = components["schemas"]["MediaAssetPatch"];
export type ResearchOverview = components["schemas"]["ResearchOverview"];
export type ResearchDataQuality = components["schemas"]["ResearchDataQuality"];
export type ResearchBucket = components["schemas"]["ResearchBucket"];
export type ResearchVersionBucket = components["schemas"]["ResearchVersionBucket"];

// ---------- 请求体 ----------
export type PatientCreate = components["schemas"]["PatientCreate"];
export type AssessmentCreate = components["schemas"]["AssessmentCreate"];
export type PrescriptionCreate = components["schemas"]["PrescriptionCreate"];
export type PrescriptionPatch = components["schemas"]["PrescriptionPatch"];
export type SessionRecordCreate = components["schemas"]["SessionRecordCreate"];

// 契约内联定义的请求体，同样从 paths 派生，避免手抄。
export type BindRequestBody =
  paths["/auth/bind"]["post"]["requestBody"]["content"]["application/json"];
export type RefreshRequestBody =
  paths["/auth/refresh"]["post"]["requestBody"]["content"]["application/json"];
export type StageConfirmationCreate =
  paths["/patients/{patientId}/stage-confirmations"]["post"]["requestBody"]["content"]["application/json"];
export type SessionBatchRequest =
  paths["/sessions"]["post"]["requestBody"]["content"]["application/json"];
export type AlertUpdate =
  paths["/alerts/{alertId}"]["patch"]["requestBody"]["content"]["application/json"];
export type MediaAssetListQuery =
  paths["/media-assets"]["get"]["parameters"]["query"];
export type MediaAssetUploadForm =
  paths["/media-assets/upload"]["post"]["requestBody"]["content"]["multipart/form-data"];

// ---------- 查询参数 ----------
export type PatientListQuery = NonNullable<
  paths["/patients"]["get"]["parameters"]["query"]
>;
export type ExerciseListQuery = NonNullable<
  paths["/exercises"]["get"]["parameters"]["query"]
>;
export type SessionListQuery = NonNullable<
  paths["/sessions"]["get"]["parameters"]["query"]
>;
export type AlertListQuery = NonNullable<
  paths["/alerts"]["get"]["parameters"]["query"]
>;
export type PrescriptionListQuery = NonNullable<
  paths["/prescriptions"]["get"]["parameters"]["query"]
>;
