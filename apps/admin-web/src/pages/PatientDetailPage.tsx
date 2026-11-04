import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { getPatient, listSessions } from "../api/operations";
import { findCurrentPrescription } from "../api/prescription-query";
import { formatServerTime } from "../api/time";
import type { Patient, Prescription, SessionRecord } from "../api/types";
import { useAuth } from "../auth/AuthProvider";

const RISK_LABEL: Record<string, string> = {
  low: "低风险",
  medium: "中风险",
  high: "高风险",
};

const RISK_CLASS: Record<string, string> = {
  low: "badge badge--success",
  medium: "badge badge--warning",
  high: "badge badge--danger",
};

export default function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const { can } = useAuth();
  const isTherapist = can("rehab_therapist");
  const encodedId = encodeURIComponent(patientId ?? "");

  const [patient, setPatient] = useState<Patient | null>(null);
  const [prescription, setPrescription] = useState<Prescription | null>(null);
  const [sessions, setSessions] = useState<SessionRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [prescriptionError, setPrescriptionError] = useState<string | null>(null);
  const [sessionsError, setSessionsError] = useState<string | null>(null);

  useEffect(() => {
    if (!patientId) {
      setLoading(false);
      setError("缺少患者编号");
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    // 切换患者时必须清空上一位患者的数据，避免错误态下串档。
    setPatient(null);
    setPrescription(null);
    setSessions([]);
    setPrescriptionError(null);
    setSessionsError(null);

    Promise.all([
      getPatient(patientId),
      findCurrentPrescription(patientId),
      listSessions({ patientId, limit: 100 }),
    ]).then(([patientResult, prescriptionResult, sessionResult]) => {
      if (cancelled) {
        return;
      }
      setLoading(false);
      if (!patientResult.ok) {
        setError(patientResult.error.message);
        return;
      }
      setPatient(patientResult.data);

      if (prescriptionResult.ok) {
        // findCurrentPrescription 已按 active + 未过期筛选并翻页查找。
        setPrescription(prescriptionResult.data);
      } else {
        setPrescriptionError(prescriptionResult.error.message);
      }

      if (sessionResult.ok) {
        setSessions(sessionResult.data.items);
      } else {
        setSessionsError(sessionResult.error.message);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [patientId]);

  const confirmed = patient?.stageConfirmationStatus === "confirmed";

  return (
    <div className="page">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h1>患者详情档案</h1>
          <p className="page-description">患者编号：{patientId} · 机构康复监护中</p>
        </div>
        {isTherapist && (
          <div style={{ display: "flex", gap: 12 }}>
            <Link to={`/patients/${encodedId}/stage-confirmation`} className="btn-secondary">
              重新确认分期 (D11)
            </Link>
            <Link to={`/patients/${encodedId}/prescription`} className="btn-primary">
              调整处方 (D12)
            </Link>
          </div>
        )}
      </div>

      {error ? (
        <p role="alert" className="notice notice--danger">
          患者档案读取失败：{error}
        </p>
      ) : null}

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 24, marginBottom: 24 }}>
        <div className="card">
          <div className="card-title">基本生理与临床分期</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">患者姓名</span>
              <strong>
                {loading
                  ? "加载中…"
                  : patient
                    ? `${patient.displayName}${
                        patient.birthYear
                          ? ` (${new Date().getFullYear() - patient.birthYear} 岁)`
                          : ""
                      }`
                    : "—"}
              </strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">H-Y 临床分级</span>
              <span className="badge badge--info">
                {patient ? `H-Y ${patient.hyStage}期` : "—"}
              </span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">分期确认状态</span>
              {patient ? (
                confirmed ? (
                  <span className="badge badge--success">✓ 康复治疗师已确认</span>
                ) : (
                  <span className="badge badge--warning">⚠️ 疑似待确认 (D11)</span>
                )
              ) : (
                <span>—</span>
              )}
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">跌倒风险评级</span>
              <span className={RISK_CLASS[patient?.fallRisk ?? "low"] ?? "badge"}>
                {RISK_LABEL[patient?.fallRisk ?? "low"] ?? "—"}
              </span>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-title">
            当前有效处方{prescription ? `（版本 ${prescription.version}）` : ""}
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {loading ? (
              <p className="hint" style={{ margin: 0 }}>
                正在读取处方…
              </p>
            ) : prescriptionError ? (
              <p role="alert" className="hint" style={{ margin: 0, color: "var(--color-danger)" }}>
                处方读取失败：{prescriptionError}
              </p>
            ) : prescription ? (
              <>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span className="hint">训练条目</span>
                  <strong>
                    {prescription.items.length > 0
                      ? prescription.items.map((item) => item.exerciseCode).join("、")
                      : "—"}
                  </strong>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span className="hint">有效期至</span>
                  <span>{formatServerTime(prescription.validUntil).slice(0, 10)}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span className="hint">签发治疗师</span>
                  <span>{prescription.prescribedBy}</span>
                </div>
              </>
            ) : (
              <p className="hint" style={{ margin: 0 }}>
                该患者暂无处方记录。
              </p>
            )}
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">最近训练记录</span>
              <strong>
                {loading
                  ? "加载中…"
                  : sessionsError
                    ? "读取失败"
                    : `${sessions.length} 条${sessions.length >= 100 ? "（仅显示最近 100 条）" : ""}`}
              </strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">处方调整</span>
              <Link to={`/patients/${encodedId}/prescription`} className="btn-secondary">
                前往调整
              </Link>
            </div>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-title">近期训练有效性与质检记录 (D14, T7)</div>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>训练时间</th>
                <th>完成次数</th>
                <th>动作错误</th>
                <th>中断</th>
                <th>有效帧占比</th>
                <th>质检标记</th>
                <th>算法版本</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7}>正在读取训练记录…</td>
                </tr>
              ) : sessionsError ? (
                <tr>
                  <td colSpan={7}>训练记录读取失败：{sessionsError}</td>
                </tr>
              ) : sessions.length === 0 ? (
                <tr>
                  <td colSpan={7}>该患者暂无训练记录。</td>
                </tr>
              ) : (
                sessions.map((record) => (
                  <tr key={record.id}>
                    <td>{formatServerTime(record.recordedAt)}</td>
                    <td>{record.completionCount ?? 0} 次</td>
                    <td>{record.movementErrorCount ?? 0} 次</td>
                    <td>{record.interruptionCount ?? 0} 次</td>
                    <td>
                      {typeof record.validFrameRatio === "number" ? (
                        <strong
                          style={{
                            color:
                              record.validFrameRatio >= 0.85
                                ? "var(--color-success)"
                                : "var(--color-danger)",
                          }}
                        >
                          {(record.validFrameRatio * 100).toFixed(1)}%
                        </strong>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td>
                      {record.isDoubtful ? (
                        <span className="badge badge--warning">已存疑</span>
                      ) : (
                        <span className="badge badge--success">无存疑</span>
                      )}
                    </td>
                    <td>{record.metricAlgorithmVersion ?? "—"}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
