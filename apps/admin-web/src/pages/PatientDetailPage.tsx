import { Link, useParams } from "react-router";
import { useAuth } from "../auth/AuthProvider";

export default function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const { can } = useAuth();
  const isTherapist = can("rehab_therapist");
  const encodedId = encodeURIComponent(patientId ?? "PT-2026-081");

  return (
    <div className="page">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h1>患者详情档案</h1>
          <p className="page-description">患者编号：{patientId ?? "PT-2026-081"} · 机构康复监护中</p>
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

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 24, marginBottom: 24 }}>
        {/* Basic & Stage Card */}
        <div className="card">
          <div className="card-title">基本生理与临床分期</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">患者姓名</span>
              <strong>张建国 (72 岁)</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">H-Y 临床分级</span>
              <span className="badge badge--info">H-Y 2期 (双侧轻度受累)</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">分期确认状态</span>
              <span className="badge badge--success">✓ 康复治疗师已确认</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">跌倒风险评级</span>
              <span className="badge badge--warning">中风险 (STEADI)</span>
            </div>
          </div>
        </div>

        {/* Current Prescription */}
        <div className="card">
          <div className="card-title">当前有效处方（版本 v2.0）</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">核心训练大类</span>
              <strong>节律步态（RAS 引导）</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">听觉刺激节拍</span>
              <strong>64 BPM (个体化定制)</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">计划频次与剂量</span>
              <strong>每周 5 次 · 每次 3 组 × 12 次</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="hint">签发治疗师</span>
              <span>李主任 (2026-11-01)</span>
            </div>
          </div>
        </div>
      </div>

      {/* Recent Training Records Table */}
      <div className="card">
        <div className="card-title">近期训练有效性与质检记录 (D14, T7)</div>
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th>训练时间</th>
                <th>训练条目</th>
                <th>完成次数</th>
                <th>动作错误</th>
                <th>有效帧占比</th>
                <th>质检标记</th>
                <th>算法版本</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>今天 10:15</td>
                <td>RAS 节律踏步训练</td>
                <td>36 次</td>
                <td>1 次</td>
                <td>
                  <strong style={{ color: "var(--color-success)" }}>98.4%</strong>
                </td>
                <td>
                  <span className="badge badge--success">无存疑</span>
                </td>
                <td>MediaPipe-Lite-v1.2</td>
              </tr>
              <tr>
                <td>昨天 15:30</td>
                <td>坐位上肢躯干伸展</td>
                <td>20 次</td>
                <td>4 次</td>
                <td>
                  <strong style={{ color: "var(--color-danger)" }}>71.2%</strong>
                </td>
                <td>
                  <span className="badge badge--warning">已存疑（脚部遮挡）</span>
                </td>
                <td>MediaPipe-Lite-v1.2</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
