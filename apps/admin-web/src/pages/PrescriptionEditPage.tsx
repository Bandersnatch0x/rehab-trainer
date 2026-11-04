import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";
import {
  createPrescription,
  listExercises,
  listPrescriptions,
  updatePrescription,
} from "../api/operations";
import type { Exercise, Prescription } from "../api/types";

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
  const [current, setCurrent] = useState<Prescription | null>(null);
  const [exerciseCode, setExerciseCode] = useState<string>("");
  const [sets, setSets] = useState<number>(3);
  const [reps, setReps] = useState<number>(12);
  const [bpm, setBpm] = useState<number>(64);
  const [frequency, setFrequency] = useState<number>(5);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [successVersion, setSuccessVersion] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!patientId) {
      setLoading(false);
      setLoadError("缺少患者编号");
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    setCurrent(null);
    setExercises([]);

    Promise.all([listExercises({ limit: 100 }), listPrescriptions({ patientId })]).then(
      ([exerciseResult, prescriptionResult]) => {
        if (cancelled) {
          return;
        }
        setLoading(false);

        if (!exerciseResult.ok) {
          // 读不到训练条目就不能签发，避免用默认值拼出一个处方。
          setLoadError(`训练条目加载失败：${exerciseResult.error.message}`);
          return;
        }
        setExercises(exerciseResult.data.items);

        if (!prescriptionResult.ok) {
          // 处方读取失败时绝不能落进"新建"分支，否则会凭空多一条处方。
          setLoadError(`现有处方读取失败：${prescriptionResult.error.message}`);
          return;
        }

        // 只在读全成功后才认定"当前处方"：active 且未过期，取最新签发。
        const now = Date.now();
        const active = prescriptionResult.data.items.find(
          (item) =>
            item.status === "active" &&
            (!item.validUntil || Date.parse(item.validUntil) >= now),
        );
        setCurrent(active ?? null);

        const first = active?.items[0];
        if (first?.exerciseCode) {
          setExerciseCode(first.exerciseCode);
          const dosage = first.dosage;
          if (dosage?.sets) setSets(dosage.sets);
          if (dosage?.repetitions) setReps(dosage.repetitions);
          if (dosage?.bpm) setBpm(dosage.bpm);
          if (dosage?.frequencyPerWeek) setFrequency(dosage.frequencyPerWeek);
        } else if (exerciseResult.data.items[0]) {
          setExerciseCode(exerciseResult.data.items[0].code);
        }
      },
    );

    return () => {
      cancelled = true;
    };
  }, [patientId]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    // 同步锁：disabled 依赖重渲染，双击仍可能进第二次。
    if (submitting || loading || loadError) {
      return;
    }
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

    const edited = {
      exerciseCode,
      dosage: {
        sets,
        repetitions: reps,
        bpm,
        frequencyPerWeek: frequency,
      },
    };
    // 后端 PATCH 是整表替换：保留当前处方里除被编辑条目外的其他条目，避免误删。
    const items = current ? [edited, ...current.items.slice(1)] : [edited];
    const now = new Date();

    // 已有处方走 PATCH + If-Match；没有才新建。绝不伪造版本号。
    const result = current
      ? await updatePrescription(current.id, { items }, current.version)
      : await createPrescription({
          patientId,
          items,
          issuedAt: now.toISOString(),
          validFrom: now.toISOString(),
          validUntil: addDays(now, 30),
          // 演示版映射表版本；真实版本应由分期→条目映射表提供（C2）。
          mappingTableVersion: "demo.1",
        });

    setSubmitting(false);
    if (result.ok) {
      setCurrent(result.data);
      setSuccessVersion(result.data.version);
      return;
    }

    if (result.error.status === 409) {
      setError("处方版本冲突：另一端已更新，请刷新后基于最新版本重试。");
      // 重新拉取最新版本，避免用户拿旧版本反复重试。
      const latest = await listPrescriptions({ patientId });
      if (latest.ok) {
        setCurrent(latest.data.items.find((item) => item.status === "active") ?? null);
      }
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
          {loading
            ? "正在读取现有处方…"
            : loadError
              ? "现有处方读取失败，已禁用签发。"
              : current
                ? `当前处方版本 ${current.version}。保存时以该版本作为 If-Match；另一端先更新则返回 409 并提示冲突。`
                : "该患者暂无有效处方，本次保存将新建 v1.0.0。"}
        </div>
      </div>

      {loadError ? (
        <p role="alert" className="notice notice--danger">
          {loadError}。请刷新重试；读取成功前不会创建新处方。
        </p>
      ) : null}

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

          {current && current.items.length > 1 ? (
            <div className="notice">
              <div>
                <strong>其余 {current.items.length - 1} 个训练条目将保持不变：</strong>
                {current.items
                  .slice(1)
                  .map((item) => item.exerciseCode)
                  .join("、")}
              </div>
            </div>
          ) : null}

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
            <button
              type="submit"
              className="btn-primary"
              disabled={submitting || loading || Boolean(loadError)}
            >
              {submitting ? "正在签发…" : "💾 签发并同步处方"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
