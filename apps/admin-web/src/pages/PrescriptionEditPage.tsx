import { useEffect, useRef, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router";
import {
  createPrescription,
  listExercises,
  updatePrescription,
} from "../api/operations";
import { findCurrentPrescription } from "../api/prescription-query";
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

const DEFAULT_DOSAGE = { sets: 3, reps: 12, bpm: 64, frequency: 5 };

type EditableItem = {
  rowId: number;
  exerciseCode: string;
  variantCode?: string;
  sets: number;
  reps: number;
  bpm: number;
  frequency: number;
  /** 用户是否改过该行；未改动的行按原样回传，避免把 UI 默认值写进空训练量。 */
  touched: boolean;
  originalDosage?: Prescription["items"][number]["dosage"];
};

/** 把训练方案条目转成可编辑行；缺失训练量用保守默认值展示，但不代表已保存。 */
function toEditable(
  item: Prescription["items"][number] | undefined,
  fallbackCode: string,
  rowId: number,
  touched = false,
): EditableItem {
  return {
    rowId,
    exerciseCode: item?.exerciseCode ?? fallbackCode,
    ...(item?.variantCode ? { variantCode: item.variantCode } : {}),
    sets: item?.dosage?.sets ?? DEFAULT_DOSAGE.sets,
    reps: item?.dosage?.repetitions ?? DEFAULT_DOSAGE.reps,
    bpm: item?.dosage?.bpm ?? DEFAULT_DOSAGE.bpm,
    frequency: item?.dosage?.frequencyPerWeek ?? DEFAULT_DOSAGE.frequency,
    touched,
    originalDosage: item?.dosage ?? undefined,
  };
}

export default function PrescriptionEditPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const navigate = useNavigate();
  const nextRowId = useRef(1);

  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [current, setCurrent] = useState<Prescription | null>(null);
  const [items, setItems] = useState<EditableItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [successVersion, setSuccessVersion] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  /** 把训练方案内容灌进表单；409 后重拉也用同一套逻辑，避免用旧值覆盖对方改动。 */
  function hydrate(prescription: Prescription | null, available: Exercise[]) {
    setCurrent(prescription);
    if (prescription && prescription.items.length > 0) {
      setItems(
        prescription.items.map((item) =>
          toEditable(item, item.exerciseCode, nextRowId.current++),
        ),
      );
    } else if (available[0]) {
      setItems([toEditable(undefined, available[0].code, nextRowId.current++, true)]);
    } else {
      setItems([]);
    }
  }

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
    setItems([]);
    setSuccessVersion(null);
    setError(null);
    setSubmitting(false);

    Promise.all([listExercises({ limit: 100 }), findCurrentPrescription(patientId)]).then(
      ([exerciseResult, prescriptionResult]) => {
        if (cancelled) {
          return;
        }
        setLoading(false);

        if (!exerciseResult.ok) {
          // 读不到训练条目就不能保存，避免用默认值拼出一个训练方案。
          setLoadError(`训练条目加载失败：${exerciseResult.error.message}`);
          return;
        }
        setExercises(exerciseResult.data.items);

        if (!prescriptionResult.ok) {
          // 训练方案读取失败时绝不能落进"新建"分支，否则会凭空多一条训练方案。
          setLoadError(`现有训练方案读取失败：${prescriptionResult.error.message}`);
          return;
        }

        hydrate(prescriptionResult.data, exerciseResult.data.items);
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
      setError("缺少患者编号，无法保存训练方案");
      return;
    }
    if (items.length === 0) {
      setError("训练方案至少保留一个训练条目");
      return;
    }
    if (items.some((item) => !item.exerciseCode)) {
      setError("请为每个训练条目选择动作");
      return;
    }
    // 后端不校验重复编码，整表替换会落成两个相同条目，这里先挡住。
    const codes = items.map((item) => item.exerciseCode);
    if (new Set(codes).size !== codes.length) {
      setError("同一训练条目不能重复添加。");
      return;
    }
    const invalid = items.find(
      (item) =>
        !Number.isFinite(item.sets) ||
        !Number.isFinite(item.reps) ||
        !Number.isFinite(item.bpm) ||
        !Number.isFinite(item.frequency) ||
        item.sets < 1 ||
        item.sets > 10 ||
        item.reps < 1 ||
        item.reps > 50 ||
        item.bpm < 40 ||
        item.bpm > 120 ||
        item.frequency < 1 ||
        item.frequency > 7,
    );
    if (invalid) {
      setError(`训练条目 ${invalid.exerciseCode} 的训练量超出允许范围。`);
      return;
    }

    setSubmitting(true);
    setError(null);

    const payloadItems = items.map((item) => ({
      exerciseCode: item.exerciseCode,
      // 条目未换时保留原有难度变体，避免一次训练量调整把变体清掉。
      ...(item.variantCode ? { variantCode: item.variantCode } : {}),
      // 未改动的行原样回传；原训练量为空时不要写入 UI 默认值。
      ...(item.touched
        ? {
            dosage: {
              sets: item.sets,
              repetitions: item.reps,
              bpm: item.bpm,
              frequencyPerWeek: item.frequency,
            },
          }
        : item.originalDosage
          ? { dosage: item.originalDosage }
          : {}),
    }));
    const now = new Date();

    // 已有训练方案走 PATCH + If-Match；没有才新建。绝不伪造版本号。
    const result = current
      ? await updatePrescription(current.id, { items: payloadItems }, current.version)
      : await createPrescription({
          patientId,
          items: payloadItems,
          issuedAt: now.toISOString(),
          validFrom: now.toISOString(),
          validUntil: addDays(now, 30),
          // 演示版映射表版本；真实版本应由分级→条目映射表提供（C2）。
          mappingTableVersion: "demo.1",
        });

    setSubmitting(false);
    if (result.ok) {
      setCurrent(result.data);
      setSuccessVersion(result.data.version);
      return;
    }

    if (result.error.status === 409) {
      setError("训练方案版本冲突：另一端已更新。已载入最新版本，请确认后重新提交。");
      // 只更新 current 而不重灌表单，会让下一次提交用旧值覆盖对方刚改的条目。
      const latest = await findCurrentPrescription(patientId);
      if (latest.ok) {
        hydrate(latest.data, exercises);
      }
      return;
    }
    setError(result.error.message);
  }

  function updateItem(rowId: number, patch: Partial<EditableItem>) {
    setItems((prev) =>
      prev.map((item) =>
        item.rowId === rowId ? { ...item, ...patch, touched: true } : item,
      ),
    );
  }

  function addItem() {
    const rowId = nextRowId.current++;
    setItems((prev) => {
      // 在函数式更新里重算已用编码，连续快速点击也不会选出同一条。
      const used = new Set(prev.map((item) => item.exerciseCode));
      const next = exercises.find((exercise) => !used.has(exercise.code));
      if (!next) {
        return prev;
      }
      return [...prev, toEditable(undefined, next.code, rowId, true)];
    });
  }

  function removeItem(rowId: number) {
    // 按稳定行 ID 删除：重复触发只会删到同一行，不会误删移位后的下一行。
    setItems((prev) =>
      prev.length > 1 ? prev.filter((item) => item.rowId !== rowId) : prev,
    );
  }

  const canAdd = exercises.some(
    (exercise) => !items.some((item) => item.exerciseCode === exercise.code),
  );

  return (
    <div className="page" style={{ maxWidth: 960 }}>
      <h1>康复训练方案制定与调整</h1>
      <p className="page-description">
        患者编号：<strong>{patientId}</strong> · 康复治疗师专属训练方案制定权限 (D12)
      </p>

      <div className="notice">
        <div>
          <strong>训练方案版本乐观锁提示（D12 约束）：</strong>
          {loading
            ? "正在读取现有训练方案…"
            : loadError
              ? "现有训练方案读取失败，已禁用保存。"
              : current
                ? `当前训练方案版本 ${current.version}。保存时以该版本作为 If-Match；另一端先更新则返回 409 并提示冲突。`
                : "该患者暂无有效训练方案，本次保存将新建 v1.0.0。"}
        </div>
      </div>

      {loadError ? (
        <p role="alert" className="notice notice--danger">
          {loadError}。请刷新重试；读取成功前不会创建新训练方案。
        </p>
      ) : null}

      {successVersion ? (
        <div className="card" style={{ textAlign: "center", padding: "40px 24px" }}>
          <div style={{ fontSize: 48, color: "var(--color-success)", marginBottom: 12 }}>✓</div>
          <h2>训练方案保存成功（版本 {successVersion}）</h2>
          <p className="hint" style={{ margin: "12px 0 24px" }}>
            患者端（Android App 与微信小程序）在下次同步时拉取新训练方案，训练计划随之生效。
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
            <div className="card-title">1. 训练条目与训练量（逐条可调）</div>
            <p className="hint" style={{ margin: "0 0 12px" }}>
              未修改的条目按原训练量保存；原训练量为空时不会把页面默认值写入。
            </p>
            {items.map((item, index) => {
              const usedElsewhere = new Set(
                items
                  .filter((other) => other.rowId !== item.rowId)
                  .map((other) => other.exerciseCode),
              );
              const options = exercises.filter(
                (exercise) =>
                  exercise.code === item.exerciseCode ||
                  !usedElsewhere.has(exercise.code),
              );
              return (
                <div
                  key={item.rowId}
                  role="group"
                  aria-label={`训练条目 ${index + 1}`}
                  style={{
                    borderTop: index > 0 ? "1px solid var(--color-border)" : undefined,
                    paddingTop: index > 0 ? 16 : 0,
                    marginTop: index > 0 ? 16 : 0,
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      gap: 12,
                      alignItems: "flex-end",
                      flexWrap: "wrap",
                    }}
                  >
                    <label style={{ flex: "2 1 240px" }}>
                      <span>训练条目：</span>
                      <select
                        value={item.exerciseCode}
                        onChange={(event) =>
                          updateItem(item.rowId, {
                            exerciseCode: event.target.value,
                            variantCode: undefined,
                          })
                        }
                      >
                        {options.length === 0 ? (
                          <option value="">（训练条目加载中或为空）</option>
                        ) : (
                          options.map((exercise) => (
                            <option key={exercise.code} value={exercise.code}>
                              {exercise.label}（
                              {CATEGORY_LABEL[exercise.category] ?? exercise.category}）
                            </option>
                          ))
                        )}
                      </select>
                    </label>

                    <label>
                      <span>组数 (Sets)：</span>
                      <input
                        type="number"
                        min={1}
                        max={10}
                        value={item.sets}
                        onChange={(event) =>
                          updateItem(item.rowId, { sets: Number(event.target.value) })
                        }
                        required
                      />
                    </label>

                    <label>
                      <span>每组次数 (Reps)：</span>
                      <input
                        type="number"
                        min={1}
                        max={50}
                        value={item.reps}
                        onChange={(event) =>
                          updateItem(item.rowId, { reps: Number(event.target.value) })
                        }
                        required
                      />
                    </label>

                    <label>
                      <span>听觉刺激 (BPM)：</span>
                      <input
                        type="number"
                        min={40}
                        max={120}
                        value={item.bpm}
                        onChange={(event) =>
                          updateItem(item.rowId, { bpm: Number(event.target.value) })
                        }
                        required
                      />
                    </label>

                    <label>
                      <span>每周频次 (天)：</span>
                      <input
                        type="number"
                        min={1}
                        max={7}
                        value={item.frequency}
                        onChange={(event) =>
                          updateItem(item.rowId, { frequency: Number(event.target.value) })
                        }
                        required
                      />
                    </label>

                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => removeItem(item.rowId)}
                      disabled={items.length === 1}
                    >
                      移除
                    </button>
                  </div>
                </div>
              );
            })}

            <div style={{ marginTop: 16, display: "flex", gap: 12, alignItems: "center" }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={addItem}
                disabled={!canAdd}
              >
                ＋ 添加训练条目
              </button>
              <span className="hint">
                {canAdd ? `当前 ${items.length} 条` : "训练条目库已全部加入"}
              </span>
            </div>
          </div>

          <div className="card" style={{ borderLeft: "6px solid var(--color-warning)" }}>
            <div className="card-title">2. 禁忌症与危险信号核验</div>
            <p className="hint">
              条目禁忌与危险信号以服务端训练条目库为准（本页不再硬编码分级结论）。
              出现头晕或胸闷时立即停止训练。
            </p>
          </div>

          {error ? (
            <p role="alert" className="notice notice--danger">
              训练方案保存失败：{error}
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
              {submitting ? "正在保存…" : "💾 保存并同步训练方案"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
