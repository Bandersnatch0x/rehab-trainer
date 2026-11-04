import { Link } from "react-router";
import { useAuth } from "../auth/AuthProvider";

interface MockPatient {
  id: string;
  name: string;
  age: number;
  stage: number;
  isConfirmed: boolean;
  fallRisk: string;
  lastSession: string;
}

const MOCK_PATIENTS: MockPatient[] = [
  {
    id: "PT-2026-081",
    name: "张建国",
    age: 72,
    stage: 2,
    isConfirmed: true,
    fallRisk: "中风险",
    lastSession: "今天 10:15 (有效帧 98%)",
  },
  {
    id: "PT-2026-085",
    name: "王秀英",
    age: 68,
    stage: 3,
    isConfirmed: false,
    fallRisk: "高风险",
    lastSession: "昨天 16:20 (质检存疑)",
  },
  {
    id: "PT-2026-092",
    name: "李素芬",
    age: 75,
    stage: 2,
    isConfirmed: true,
    fallRisk: "低风险",
    lastSession: "前天 09:30 (有效帧 94%)",
  },
];

export default function PatientsPage() {
  const { can } = useAuth();
  const isTherapist = can("rehab_therapist");

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
          <select defaultValue="">
            <option value="">全部 (1–5 级)</option>
            <option value="1">H-Y 1期 (单侧受累)</option>
            <option value="2">H-Y 2期 (双侧轻度)</option>
            <option value="3">H-Y 3期 (姿势不稳)</option>
            <option value="4">H-Y 4期 (严重受障)</option>
          </select>
        </label>

        <label style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
          <span>确认状态：</span>
          <select defaultValue="">
            <option value="">全部</option>
            <option value="confirmed">已确认（治疗师已签发）</option>
            <option value="unconfirmed">疑似（待确认）</option>
          </select>
        </label>

        <span className="hint" style={{ marginLeft: "auto" }}>
          共 {MOCK_PATIENTS.length} 位在管患者
        </span>
      </div>

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
              <th>最近训练</th>
              <th>临床操作</th>
            </tr>
          </thead>
          <tbody>
            {MOCK_PATIENTS.map((p) => (
              <tr key={p.id}>
                <td>
                  <strong>{p.id}</strong>
                </td>
                <td>
                  <strong>{p.name}</strong>
                </td>
                <td>{p.age} 岁</td>
                <td>
                  <span className="badge badge--info">H-Y {p.stage}期</span>
                </td>
                <td>
                  {p.isConfirmed ? (
                    <span className="badge badge--success">✓ 治疗师已确认</span>
                  ) : (
                    <span className="badge badge--warning">⚠️ 疑似待确认 (D11)</span>
                  )}
                </td>
                <td>
                  <span
                    className={
                      p.fallRisk === "高风险"
                        ? "badge badge--danger"
                        : p.fallRisk === "中风险"
                        ? "badge badge--warning"
                        : "badge badge--success"
                    }
                  >
                    {p.fallRisk}
                  </span>
                </td>
                <td>{p.lastSession}</td>
                <td>
                  <div style={{ display: "flex", gap: 12 }}>
                    <Link to={`/patients/${encodeURIComponent(p.id)}`} className="btn-secondary" style={{ padding: "4px 10px", fontSize: 13, minHeight: 32 }}>
                      详情
                    </Link>
                    {isTherapist && !p.isConfirmed && (
                      <Link
                        to={`/patients/${encodeURIComponent(p.id)}/stage-confirmation`}
                        className="btn-primary"
                        style={{ padding: "4px 10px", fontSize: 13, minHeight: 32 }}
                      >
                        分期确认
                      </Link>
                    )}
                    {isTherapist && p.isConfirmed && (
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
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
