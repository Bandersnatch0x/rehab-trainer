import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";
import { createPrescription, listExercises } from "../api/operations";
import type { Exercise } from "../api/types";

const CATEGORY_LABEL: Record<string, string> = {
  strength: "肌力维持（防肌少症）",
  stretching: "牵伸放松（缓解僵直）",
  gait: "节律步态（RAS 引导）",
  posture: "姿势矫正（躯干对齐）",
};

function addDays(from: Date, days: number): string {
  const next = new Date(from.getTime() + days * 24 * 60 * 60 * 1000);
  return next.toISOString();
}

export default function PrescriptionEditPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const navigate = useNavigate();

  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [exerciseCode, setExerciseCode] = useState<string>("");
  const [sets, setSets] = useState<number>(3);
  const [reps, setReps] = useState<number>(12);
  const [bpm, setBpm] = useState<number>(64);
  const [frequency, setFrequency] = useState<number>(5);
  const [submitting, setSubmitting] = useState(false);
  const [successVersion, setSuccessVersion] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listExercises({ limit: 100 }).then((result) => {
      if (cancelled || !result.ok) {
        return;
      }
      setExercises(result.data.items);
      setExerciseCode((prev) => prev || (result.data.items[0]?.code ?? ""));
    });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!patientId) {
      setError("缺少患者编号，无法签发处方");
      return;
    }
    if (!exerciseCode) {
      setError("请选择训练条目");
      return;
    }
    setSubmitting(true);
    setError(null);

    const now = new Date();
    const result = await createPrescription({
      patientId,
      items: [
        {
          exerciseCode,
          dosage: {
            sets,
            repetitions: reps,
            bpm,
            frequencyPerWeek: frequency,
          },
        },
      ],
      issuedAt: now.toISOString(),
      validFrom: now.toISOString(),
      validUntil: addDays(now, 30),
      mappingTableVersion: "demo.1",
    });

    setSubmitting(false);
    if (result.ok) {
      setSuccessVersion(result.data.version);
      return;
    }
    setError(result.error.message);
  }

  return (
    <div className="page" style={{ maxWidth: 960 }}>
      <h1>康复训练处方开具与调整</h1>
      <p className="page-description">
        患者编号：<strong>{patientId}</strong> · 康复治疗师临床专属处方权 (D12)
      </p>

      <div className="notice">
        <div>
          <strong>处方版本乐观锁提示（D12 约束）：</strong>
          契约当前只提供开具（POST）与调整（PATCH，需 If-Match 版本号），没有按患者读取当前处方的操作；
          本页只执行开具新版本，避免在拿不到版本号时伪造乐观锁。
        </div>
      </div>

      {successVersion ? (
        <div className="card" style={{ textAlign: "center", padding: "40px 24px" }}>
          <div style={{ fontSize: 48, color: "var(--color-success)", marginBottom: 12 }}>✓</div>
          <h2>处方签发成功（版本 {successVersion}）</h2>
          <p className="hint" style={{ margin: "12px 0 24px" }}>
            患者端（Android App 与微信小程序）在下次同步时拉取新处方，训练计划随之生效。
          </p>
          <button
            type="button"
            className="btn-primary"
            onClick={() => navigate(`/patients/${encodeURIComponent(patientId ?? "")}`)}
          >
            返回患者档案
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit}>
          <div className="card">
            <div className="card-title">1. 选择训练条目</div>
            <label>
              <span>训练条目：</span>
              <select
                value={exerciseCode}
                onChange={(event) => setExerciseCode(event.target.value)}
              >
                {exercises.length === 0 ? (
                  <option value="">（训练条目加载中或为空）</option>
                ) : (
                  exercises.map((exercise) => (
                    <option key={exercise.code} value={exercise.code}>
                      {exercise.label}（{CATEGORY_LABEL[exercise.category] ?? exercise.category}）
                    </option>
                  ))
                )}
              </select>
            </label>
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
                  onChange={(event) => setSets(Number(event.target.value))}
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
                  onChange={(event) => setReps(Number(event.target.value))}
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
                  onChange={(event) => setBpm(Number(event.target.value))}
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
                  onChange={(event) => setFrequency(Number(event.target.value))}
                  required
                />
              </label>
            </div>
          </div>

          <div className="card" style={{ borderLeft: "6px solid var(--color-warning)" }}>
            <div className="card-title">3. 禁忌症与危险信号核验</div>
            <p className="hint">
              条目禁忌与危险信号以服务端训练条目库为准（本页不再硬编码分期结论）。
              出现头晕或胸闷时立即停止训练。
            </p>
          </div>

          {error ? (
            <p role="alert" className="notice notice--danger">
              处方签发失败：{error}
            </p>
          ) : null}

          <div style={{ display: "flex", gap: 16 }}>
            <button type="button" className="btn-secondary" onClick={() => navigate(-1)}>
              取消
            </button>
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting ? "正在签发…" : "💾 签发并同步处方"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
