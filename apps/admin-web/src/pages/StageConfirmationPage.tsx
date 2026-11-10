import { useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";
import { confirmStage } from "../api/operations";

export default function StageConfirmationPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const navigate = useNavigate();
  // 必须由治疗师显式选择分级：默认 2 期会让未作判断的提交落成真实临床记录。
  const [stageValue, setStageValue] = useState<number | "">("");
  // 查体意见必须来自本次实际检查，不能预填一段患者特定的虚构描述。
  const [clinicalNotes, setClinicalNotes] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    // 同步锁：disabled 要等重渲染，快速双击可能进第二次。
    if (submitting) {
      return;
    }
    if (!patientId) {
      setError("缺少患者编号，无法保存分级");
      return;
    }
    if (stageValue === "") {
      setError("请选择 H-Y 分级后再提交");
      return;
    }
    setSubmitting(true);
    setError(null);

    const result = await confirmStage(patientId, {
      stage: stageValue,
      confirmedAt: new Date().toISOString(),
      note: clinicalNotes,
    });

    setSubmitting(false);
    if (result.ok) {
      setSuccess(true);
      return;
    }
    setError(result.error.message);
  };

  return (
    <div className="page" style={{ maxWidth: 880 }}>
      <h1>H-Y 分级确认</h1>
      <p className="page-description">
        患者编号：<strong>{patientId ?? "—"}</strong> · 康复治疗师康复权威判定
      </p>

      <div className="notice notice--warning">
        <div>
          <strong>分级确认硬约束（D11）：</strong>
          未经治疗师确认为合法分级的患者，系统一律标记为「疑似」，高风险训练条目与站立位高负荷方案不得准入。
        </div>
      </div>

      {success ? (
        <div className="card" style={{ textAlign: "center", padding: "40px 24px" }}>
          <div style={{ fontSize: 48, color: "var(--color-success)", marginBottom: 12 }}>✓</div>
          <h2>H-Y 分级确认已保存</h2>
          <p className="hint" style={{ margin: "12px 0 24px" }}>
            系统已更新该患者档案为「已确认（H-Y {stageValue}期）」，请前往患者档案确认并调整训练方案。
          </p>
          <button
            type="button"
            className="btn-primary"
            onClick={() => navigate(`/patients/${encodeURIComponent(patientId ?? "PT-2026-085")}`)}
          >
            返回患者档案
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="card">
          <label>
            <span>判定 H-Y 分级（1–5 级）：</span>
            <select
              value={stageValue}
              onChange={(e) => setStageValue(Number(e.target.value))}
              style={{ fontWeight: 700, fontSize: 16 }}
            >
              <option value="" disabled>
                请选择分级
              </option>
              <option value={1}>1 期 - 仅单侧受累，通常伴有极少或无功能损害</option>
              <option value={2}>2 期 - 双侧或中线受累，无平衡功能障碍 (准入节律步态训练)</option>
              <option value={3}>3 期 - 双侧受累并伴轻至中度姿势不稳定，生活可自理 (禁无支撑站立)</option>
              <option value={4}>4 期 - 严重残障，能在无协助下站立或行走，但极为吃力</option>
              <option value={5}>5 期 - 轮椅受限或卧床，仅作宣教与转诊，不进训练流程</option>
            </select>
          </label>

          <label>
            <span>康复查体与步态症状评定意见：</span>
            <textarea
              rows={4}
              value={clinicalNotes}
              onChange={(e) => setClinicalNotes(e.target.value)}
              required
            />
          </label>

          <div className="notice">
            <div>
              <strong>禁忌与安全规则自动核验：</strong>
              若判定为 H-Y 3–4 级，系统将强制锁定站立位高难度动作，并要求照护人员近身在场。
            </div>
          </div>

          {error ? (
            <p role="alert" className="notice notice--danger">
              分级保存失败：{error}
            </p>
          ) : null}

          <div style={{ display: "flex", gap: 16, marginTop: 12 }}>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => navigate(-1)}
            >
              取消
            </button>
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting ? "提交中…" : "✓ 确认 H-Y 分级 (StageConfirmation)"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
