import { useState } from "react";

interface AlertRecord {
  id: string;
  patientId: string;
  patientName: string;
  time: string;
  ruleCode: string;
  severity: "critical" | "warning" | "info";
  description: string;
  status: "待处理" | "已确认" | "已随访";
  channel: string;
}

const MOCK_ALERTS: AlertRecord[] = [
  {
    id: "ALT-2026-001",
    patientId: "PT-2026-081",
    patientName: "张建国",
    time: "今日 09:20",
    ruleCode: "RULE-RIGIDITY-03",
    severity: "critical",
    description: "自评主观晨僵感连续 3 天加重，超过临床预警阈值",
    status: "待处理",
    channel: "端上弹窗 + 微信服务通知",
  },
  {
    id: "ALT-2026-002",
    patientId: "PT-2026-085",
    patientName: "王秀英",
    time: "昨日 14:10",
    ruleCode: "RULE-FALL-01",
    severity: "warning",
    description: "STEADI 跌倒风险自评出现单项异常，步态平衡不稳",
    status: "已随访",
    channel: "微信服务通知",
  },
  {
    id: "ALT-2026-003",
    patientId: "PT-2026-092",
    patientName: "李素芬",
    time: "前天 16:45",
    ruleCode: "RULE-FRAME-QUALITY-02",
    severity: "info",
    description: "连续两轮训练有效帧占比低于 75%，疑似摄像头角度偏移",
    status: "已确认",
    channel: "系统消息",
  },
];

export default function AlertsPage() {
  const [filter, setFilter] = useState<string>("all");
  const [alerts, setAlerts] = useState<AlertRecord[]>(MOCK_ALERTS);

  const filteredAlerts = alerts.filter((a) => {
    if (filter === "all") return true;
    return a.severity === filter;
  });

  const handleResolve = (id: string) => {
    setAlerts((prev) =>
      prev.map((item) => (item.id === id ? { ...item, status: "已随访" } : item))
    );
  };

  return (
    <div className="page">
      <h1>预警处置中心</h1>
      <p className="page-description">
        由患者症状自评阈值与训练动作异常触发的安全事件，康复医护需及时处置并登记随访台账（D6）。
      </p>

      <div className="notice notice--danger">
        <div>
          <strong>预警阻断与随访纪律：</strong>
          严重级别预警触发后，患者端将自动弹出红色提示并附带语音停止指引；机构管理员须在 24 小时内电话随访排查。
        </div>
      </div>

      <div className="card" style={{ padding: "16px 20px", display: "flex", gap: 12, alignItems: "center" }}>
        <button
          className={filter === "all" ? "btn-primary" : "btn-secondary"}
          style={{ minHeight: 36, padding: "4px 14px", fontSize: 14 }}
          onClick={() => setFilter("all")}
        >
          全部 ({alerts.length})
        </button>
        <button
          className={filter === "critical" ? "btn-danger" : "btn-secondary"}
          style={{ minHeight: 36, padding: "4px 14px", fontSize: 14 }}
          onClick={() => setFilter("critical")}
        >
          严重 ({alerts.filter((a) => a.severity === "critical").length})
        </button>
        <button
          className={filter === "warning" ? "btn-secondary" : "btn-secondary"}
          style={{
            minHeight: 36,
            padding: "4px 14px",
            fontSize: 14,
            borderColor: filter === "warning" ? "var(--color-warning)" : undefined,
            color: filter === "warning" ? "var(--color-warning)" : undefined,
          }}
          onClick={() => setFilter("warning")}
        >
          警告 ({alerts.filter((a) => a.severity === "warning").length})
        </button>
        <button
          className={filter === "info" ? "btn-secondary" : "btn-secondary"}
          style={{ minHeight: 36, padding: "4px 14px", fontSize: 14 }}
          onClick={() => setFilter("info")}
        >
          提示 ({alerts.filter((a) => a.severity === "info").length})
        </button>
      </div>

      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>级别</th>
              <th>患者</th>
              <th>触发时间</th>
              <th>触发规则</th>
              <th>事件描述</th>
              <th>通知渠道</th>
              <th>处置状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {filteredAlerts.map((a) => (
              <tr key={a.id}>
                <td>
                  <span
                    className={
                      a.severity === "critical"
                        ? "badge badge--danger"
                        : a.severity === "warning"
                        ? "badge badge--warning"
                        : "badge badge--info"
                    }
                  >
                    {a.severity === "critical" ? "严重" : a.severity === "warning" ? "警告" : "提示"}
                  </span>
                </td>
                <td>
                  <strong>{a.patientName}</strong> ({a.patientId})
                </td>
                <td>{a.time}</td>
                <td><code>{a.ruleCode}</code></td>
                <td>{a.description}</td>
                <td><span className="hint">{a.channel}</span></td>
                <td>
                  <span
                    className={
                      a.status === "待处理"
                        ? "badge badge--danger"
                        : a.status === "已随访"
                        ? "badge badge--success"
                        : "badge badge--info"
                    }
                  >
                    {a.status}
                  </span>
                </td>
                <td>
                  {a.status === "待处理" ? (
                    <button
                      className="btn-primary"
                      style={{ padding: "4px 12px", minHeight: 32, fontSize: 13 }}
                      onClick={() => handleResolve(a.id)}
                    >
                      登记随访
                    </button>
                  ) : (
                    <span className="hint">已归档</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
