import { useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";

export default function PrescriptionEditPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const navigate = useNavigate();

  const [category, setCategory] = useState<string>("节律步态");
  const [exerciseCode, setExerciseCode] = useState<string>("EX-RAS-STEP-01");
  const [sets, setSets] = useState<number>(3);
  const [reps, setReps] = useState<number>(12);
  const [bpm, setBpm] = useState<number>(64);
  const [frequency, setFrequency] = useState<number>(5);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setTimeout(() => {
      setSubmitting(false);
      setSuccess(true);
    }, 600);
  };

  return (
    <div className="page" style={{ maxWidth: 960 }}>
      <h1>康复训练处方开具与调整</h1>
      <p className="page-description">
        患者编号：<strong>{patientId ?? "PT-2026-081"}</strong> · 康复治疗师临床专属处方权 (D12)
      </p>

      <div className="notice">
        <div>
          <strong>处方版本乐观锁提示（D12 约束）：</strong>
          系统基础方案已按分期与症状自动匹配。保存时携带当前版本号（Version: 2），避免多终端并发冲突。
        </div>
      </div>

      {success ? (
        <div className="card" style={{ textAlign: "center", padding: "40px 24px" }}>
          <div style={{ fontSize: 48, color: "var(--color-success)", marginBottom: 12 }}>✓</div>
          <h2>新版本处方签发成功 (v3.0)</h2>
          <p className="hint" style={{ margin: "12px 0 24px" }}>
            患者端（Android App 与微信小程序）已实时同步新处方，训练计划即时生效。
          </p>
          <button
            type="button"
            className="btn-primary"
            onClick={() => navigate(`/patients/${encodeURIComponent(patientId ?? "PT-2026-081")}`)}
          >
            返回患者档案
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit}>
          <div className="card">
            <div className="card-title">1. 选择训练大类与训练条目（4大固定大类）</div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              <label>
                <span>训练大类：</span>
                <select value={category} onChange={(e) => setCategory(e.target.value)}>
                  <option value="节律步态">节律步态（RAS 引导）</option>
                  <option value="牵伸放松">牵伸放松（缓解僵直）</option>
                  <option value="肌力维持">肌力维持（防肌少症）</option>
                  <option value="姿势矫正">姿势矫正（躯干对齐）</option>
                </select>
              </label>

              <label>
                <span>训练条目：</span>
                <select value={exerciseCode} onChange={(e) => setExerciseCode(e.target.value)}>
                  <option value="EX-RAS-STEP-01">原地节律踏步（RAS-Metronome-64）</option>
                  <option value="EX-STRETCH-ARM-02">坐位上肢躯干伸展</option>
                  <option value="EX-STRENGTH-LEG-01">下肢防跌倒维持性靠墙蹲</option>
                </select>
              </label>
            </div>
          </div>

          <div className="card">
            <div className="card-title">2. 训练剂量（Dosage）设定</div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: 16 }}>
              <label>
                <span>组数 (Sets)：</span>
                <input
                  type="number"
                  min={1}
                  max={10}
                  value={sets}
                  onChange={(e) => setSets(Number(e.target.value))}
                  required
                />
              </label>

              <label>
                <span>每组次数 (Reps)：</span>
                <input
                  type="number"
                  min={1}
                  max={50}
                  value={reps}
                  onChange={(e) => setReps(Number(e.target.value))}
                  required
                />
              </label>

              <label>
                <span>听觉刺激 (BPM)：</span>
                <input
                  type="number"
                  min={40}
                  max={120}
                  value={bpm}
                  onChange={(e) => setBpm(Number(e.target.value))}
                  required
                />
              </label>

              <label>
                <span>每周频次 (天)：</span>
                <input
                  type="number"
                  min={1}
                  max={7}
                  value={frequency}
                  onChange={(e) => setFrequency(Number(e.target.value))}
                  required
                />
              </label>
            </div>
          </div>

          <div className="card" style={{ borderLeft: "6px solid var(--color-warning)" }}>
            <div className="card-title">3. 禁忌症与危险信号核验</div>
            <p className="hint">
              系统已核验该患者 H-Y 2期，无无支撑跌倒禁忌。危险信号：出现头晕或胸闷时立即停止。
            </p>
          </div>

          <div style={{ display: "flex", gap: 16 }}>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => navigate(-1)}
            >
              取消
            </button>
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting ? "正在签发…" : "💾 签发并同步处方 (Prescription v3.0)"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
