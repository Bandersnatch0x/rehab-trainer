import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router";
import { createPatient, listPatients } from "../api/operations";
import type { Patient } from "../api/types";
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

export default function PatientsPage() {
  const { can } = useAuth();
  const isTherapist = can("rehab_therapist");
  const canCreate = can("care_admin") || isTherapist;

  const [stage, setStage] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [patients, setPatients] = useState<Patient[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [capped, setCapped] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  const [showCreate, setShowCreate] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createBirthYear, setCreateBirthYear] = useState("");
  const [createNote, setCreateNote] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    // 契约只支持按 stage 过滤；确认状态在本地筛选，避免臆造查询参数。
    async function loadAll() {
      const collected: Patient[] = [];
      const seen = new Set<string>();
      let cursor: string | undefined;
      do {
        const result = await listPatients({
          limit: 100,
          ...(stage ? { stage: Number(stage) } : {}),
          ...(cursor ? { cursor } : {}),
        });
        if (cancelled) {
          return;
        }
        if (!result.ok) {
          setError(result.error.message);
          setPatients([]);
          setLoading(false);
          return;
        }
        collected.push(...result.data.items);
        cursor = result.data.nextCursor ?? undefined;
        // 重复游标说明分页实现异常，继续拉会死循环。
        if (cursor && seen.has(cursor)) {
          setError("分页游标重复，已停止加载。");
          break;
        }
        if (cursor) {
          seen.add(cursor);
        }
        // 防失控：单次筛选最多拉 1000 条。
      } while (cursor && collected.length < 1000);

      setPatients(collected);
      // 只有确实还有下一页才算被上限截断，恰好 1000 条且已取完时不算。
      setCapped(Boolean(cursor));
      setLoading(false);
    }

    void loadAll();

    return () => {
      cancelled = true;
    };
  }, [stage, reloadToken]);

  async function onCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError(null);
    const name = createName.trim();
    if (!name) {
      setCreateError("请填写患者姓名。");
      return;
    }
    let birthYear: number | undefined;
    if (createBirthYear.trim()) {
      birthYear = Number(createBirthYear);
      if (!Number.isInteger(birthYear) || birthYear < 1900 || birthYear > 2100) {
        setCreateError("出生年份需为 1900 至 2100 之间的整数。");
        return;
      }
    }
    setCreating(true);
    const result = await createPatient({
      displayName: name,
      ...(birthYear ? { birthYear } : {}),
      ...(createNote.trim() ? { note: createNote.trim() } : {}),
    });
    setCreating(false);
    if (!result.ok) {
      setCreateError(result.error.message);
      return;
    }
    // 重新按当前筛选拉取，避免本地前插破坏筛选或与在途请求竞争。
    setReloadToken((value) => value + 1);
    setShowCreate(false);
    setCreateName("");
    setCreateBirthYear("");
    setCreateNote("");
  }

  const visible = patients.filter((p) =>
    confirmation === "" ? true : p.stageConfirmationStatus === confirmation,
  );

  return (
    <div className="page">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <h1>患者工作台</h1>
          <p className="page-description">机构名下帕金森患者档案、H-Y 分级确认与安全状态管理。</p>
        </div>
        {canCreate ? (
          <button
            className={showCreate ? "btn-secondary" : "btn-primary"}
            onClick={() => setShowCreate((value) => !value)}
          >
            {showCreate ? "取消新建" : "新建患者"}
          </button>
        ) : null}
      </div>

      <div className="notice">
        <div>
          <strong>多机构数据隔离（D10 约束）：</strong>
          系统严格按当前登录机构隔离患者数据，不提供跨机构切换或检索。
        </div>
      </div>

      {showCreate && canCreate ? (
        <div className="card">
          <div className="card-title">新建患者档案</div>
          <p className="hint" style={{ margin: "0 0 12px" }}>
            建档后 H-Y 分级为「疑似 1 期」，需康复治疗师完成分级确认（D11）。
          </p>
          <form
            onSubmit={onCreate}
            style={{ display: "flex", gap: 16, alignItems: "flex-end", flexWrap: "wrap" }}
          >
            <label>
              患者姓名
              <input
                value={createName}
                onChange={(event) => setCreateName(event.target.value)}
                maxLength={128}
                required
              />
            </label>
            <label>
              出生年份（可选）
              <input
                value={createBirthYear}
                onChange={(event) => setCreateBirthYear(event.target.value)}
                inputMode="numeric"
                placeholder="1950"
              />
            </label>
            <label>
              建档备注（可选）
              <input
                value={createNote}
                onChange={(event) => setCreateNote(event.target.value)}
              />
            </label>
            <button className="btn-primary" type="submit" disabled={creating}>
              {creating ? "正在建档…" : "创建档案"}
            </button>
          </form>
          {createError ? (
            <p role="alert" className="notice notice--danger" style={{ marginTop: 12 }}>
              {createError}
            </p>
          ) : null}
        </div>
      ) : null}

      <div className="card" style={{ padding: "16px 20px", display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
        <label style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
          <span>H-Y 分级：</span>
          <select
            aria-label="H-Y 分级："
            value={stage}
            onChange={(event) => setStage(event.target.value)}
          >
            <option value="">全部 (1–5 级)</option>
            <option value="1">H-Y 1期 (单侧受累)</option>
            <option value="2">H-Y 2期 (双侧轻度)</option>
            <option value="3">H-Y 3期 (姿势不稳)</option>
            <option value="4">H-Y 4期 (严重受障)</option>
            <option value="5">H-Y 5期 (轮椅/卧床，仅宣教)</option>
          </select>
        </label>

        <label style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
          <span>确认状态：</span>
          <select
            aria-label="确认状态："
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
          >
            <option value="">全部</option>
            <option value="confirmed">已确认（治疗师已确认）</option>
            <option value="suspected">疑似（待确认）</option>
          </select>
        </label>

        <span className="hint" style={{ marginLeft: "auto" }}>
          {loading
            ? "加载中…"
            : capped
              ? `已加载 ${visible.length} 位（达到 1000 条上限，请用筛选缩小范围）`
              : `共 ${visible.length} 位患者`}
        </span>
      </div>

      {error ? (
        <p role="alert" className="notice notice--danger">
          患者列表加载失败：{error}
        </p>
      ) : null}

      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>患者编号</th>
              <th>姓名</th>
              <th>年龄</th>
              <th>H-Y 分级</th>
              <th>分级确认状态</th>
              <th>跌倒风险</th>
              <th>建档时间</th>
              <th>康复操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={8}>正在读取患者档案…</td>
              </tr>
            ) : visible.length === 0 ? (
              <tr>
                <td colSpan={8}>当前筛选条件下没有患者。</td>
              </tr>
            ) : (
              visible.map((p) => {
                const confirmed = p.stageConfirmationStatus === "confirmed";
                return (
                  <tr key={p.id}>
                    <td>
                      <strong>{p.id}</strong>
                    </td>
                    <td>
                      <strong>{p.displayName}</strong>
                    </td>
                    <td>{p.birthYear ? `${new Date().getFullYear() - p.birthYear} 岁` : "—"}</td>
                    <td>
                      <span className="badge badge--info">H-Y {p.hyStage}期</span>
                    </td>
                    <td>
                      {confirmed ? (
                        <span className="badge badge--success">✓ 治疗师已确认</span>
                      ) : (
                        <span className="badge badge--warning">⚠️ 疑似待确认 (D11)</span>
                      )}
                    </td>
                    <td>
                      <span className={RISK_CLASS[p.fallRisk ?? "low"] ?? "badge"}>
                        {RISK_LABEL[p.fallRisk ?? "low"] ?? p.fallRisk}
                      </span>
                    </td>
                    <td>{p.createdAt ? p.createdAt.slice(0, 10) : "—"}</td>
                    <td>
                      <div style={{ display: "flex", gap: 12 }}>
                        <Link to={`/patients/${encodeURIComponent(p.id)}`} className="btn-secondary" style={{ padding: "4px 10px", fontSize: 13, minHeight: 32 }}>
                          详情
                        </Link>
                        {isTherapist && !confirmed && (
                          <Link
                            to={`/patients/${encodeURIComponent(p.id)}/stage-confirmation`}
                            className="btn-primary"
                            style={{ padding: "4px 10px", fontSize: 13, minHeight: 32 }}
                          >
                            分级确认
                          </Link>
                        )}
                        {isTherapist && confirmed && (
                          <Link
                            to={`/patients/${encodeURIComponent(p.id)}/prescription`}
                            className="btn-secondary"
                            style={{ padding: "4px 10px", fontSize: 13, minHeight: 32 }}
                          >
                            调训练方案
                          </Link>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
