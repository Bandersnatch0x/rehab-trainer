import { useEffect, useState } from "react";
import { Link } from "react-router";
import { listPatients } from "../api/operations";
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

  const [stage, setStage] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [patients, setPatients] = useState<Patient[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    // 契约只支持按 stage 过滤；确认状态在本地筛选，避免臆造查询参数。
    listPatients(stage ? { stage: Number(stage) } : {}).then((result) => {
      if (cancelled) {
        return;
      }
      setLoading(false);
      if (result.ok) {
        setPatients(result.data.items);
      } else {
        setError(result.error.message);
        setPatients([]);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [stage]);

  const visible = patients.filter((p) =>
    confirmation === "" ? true : p.stageConfirmationStatus === confirmation,
  );

  return (
    <div className="page">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <h1>患者工作台</h1>
          <p className="page-description">机构名下帕金森患者档案、H-Y 分期确认与安全状态管理。</p>
        </div>
      </div>

      <div className="notice">
        <div>
          <strong>多机构数据隔离（D10 约束）：</strong>
          系统严格按当前登录机构隔离患者数据，不提供跨机构切换或检索。
        </div>
      </div>

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
            <option value="confirmed">已确认（治疗师已签发）</option>
            <option value="suspected">疑似（待确认）</option>
          </select>
        </label>

        <span className="hint" style={{ marginLeft: "auto" }}>
          {loading ? "加载中…" : `共 ${visible.length} 位在管患者`}
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
              <th>分期确认状态</th>
              <th>跌倒风险</th>
              <th>建档时间</th>
              <th>临床操作</th>
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
                            分期确认
                          </Link>
                        )}
                        {isTherapist && confirmed && (
                          <Link
                            to={`/patients/${encodeURIComponent(p.id)}/prescription`}
                            className="btn-secondary"
                            style={{ padding: "4px 10px", fontSize: 13, minHeight: 32 }}
                          >
                            调处方
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
