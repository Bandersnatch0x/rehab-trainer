import { useCallback, useEffect, useState } from "react";
import { listAlerts, updateAlert } from "../api/operations";
import { formatServerTime } from "../api/time";
import type { Alert } from "../api/types";

type LevelFilter = "" | "critical" | "warning" | "info";

const LEVEL_LABEL: Record<string, string> = {
  critical: "严重",
  warning: "警告",
  info: "提示",
};

const LEVEL_CLASS: Record<string, string> = {
  critical: "badge badge--danger",
  warning: "badge badge--warning",
  info: "badge badge--info",
};

const STATUS_LABEL: Record<string, string> = {
  open: "待处理",
  acknowledged: "已确认",
  followed_up: "已随访",
  closed: "已关闭",
};

const STATUS_CLASS: Record<string, string> = {
  open: "badge badge--danger",
  acknowledged: "badge badge--info",
  followed_up: "badge badge--success",
  closed: "badge badge--info",
};

const CHANNEL_LABEL: Record<string, string> = {
  in_app_popup: "端上弹窗",
  voice_prompt: "语音提示",
  push_notification: "推送通知",
  audit_log: "审计日志",
};


export default function AlertsPage() {
  const [filter, setFilter] = useState<LevelFilter>("");
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [capped, setCapped] = useState(false);

  // 跟随游标取全量后在本地筛选：计数不会被分页截断，也不会随筛选互相污染。
  const load = useCallback(async () => {
    setLoading(true);
    setError(null);

    const collected: Alert[] = [];
    const seen = new Set<string>();
    let cursor: string | undefined;
    do {
      const result = await listAlerts({
        limit: 100,
        ...(cursor ? { cursor } : {}),
      });
      if (!result.ok) {
        setError(result.error.message);
        setAlerts([]);
        setLoading(false);
        return;
      }
      collected.push(...result.data.items);
      cursor = result.data.nextCursor ?? undefined;
      if (cursor && seen.has(cursor)) {
        setError("分页游标重复，已停止加载。");
        break;
      }
      if (cursor) {
        seen.add(cursor);
      }
    } while (cursor && collected.length < 1000);

    setAlerts(collected);
    setCapped(Boolean(cursor));
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const visible = filter === "" ? alerts : alerts.filter((a) => a.level === filter);

  async function handleStatus(
    alert: Alert,
    status: "acknowledged" | "followed_up" | "closed",
    note: string,
  ) {
    setPendingId(alert.id);
    setError(null);
    const result = await updateAlert(alert.id, { status, note });
    setPendingId(null);
    if (!result.ok) {
      setError(`预警 ${alert.id} 状态更新失败：${result.error.message}`);
      return;
    }
    // 以服务端返回为准，避免本地状态与台账不一致。
    setAlerts((prev) => prev.map((item) => (item.id === result.data.id ? result.data : item)));
  }

  const countByLevel = (level: Exclude<LevelFilter, "">) =>
    alerts.filter((a) => a.level === level).length;

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

      {error ? (
        <p role="alert" className="notice notice--danger">
          {error}
        </p>
      ) : null}

      <div className="card" style={{ padding: "16px 20px", display: "flex", gap: 12, alignItems: "center" }}>
        <button
          className={filter === "" ? "btn-primary" : "btn-secondary"}
          style={{ minHeight: 36, padding: "4px 14px", fontSize: 14 }}
          onClick={() => setFilter("")}
        >
          全部 ({alerts.length})
        </button>
        <button
          className={filter === "critical" ? "btn-danger" : "btn-secondary"}
          style={{ minHeight: 36, padding: "4px 14px", fontSize: 14 }}
          onClick={() => setFilter("critical")}
        >
          严重 ({countByLevel("critical")})
        </button>
        <button
          className="btn-secondary"
          style={{
            minHeight: 36,
            padding: "4px 14px",
            fontSize: 14,
            borderColor: filter === "warning" ? "var(--color-warning)" : undefined,
            color: filter === "warning" ? "var(--color-warning)" : undefined,
          }}
          onClick={() => setFilter("warning")}
        >
          警告 ({countByLevel("warning")})
        </button>
        <button
          className="btn-secondary"
          style={{ minHeight: 36, padding: "4px 14px", fontSize: 14 }}
          onClick={() => setFilter("info")}
        >
          提示 ({countByLevel("info")})
        </button>
        {loading ? (
          <span className="hint">加载中…</span>
        ) : capped ? (
          <span className="hint">已达 1000 条上限，请按患者或状态缩小范围</span>
        ) : null}
      </div>

      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>级别</th>
              <th>患者</th>
              <th>触发时间</th>
              <th>触发规则</th>
              <th>通知渠道</th>
              <th>处置状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7}>正在读取预警台账…</td>
              </tr>
            ) : visible.length === 0 ? (
              <tr>
                <td colSpan={7}>当前筛选条件下没有预警。</td>
              </tr>
            ) : (
              visible.map((a) => (
                <tr key={a.id}>
                  <td>
                    <span className={LEVEL_CLASS[a.level] ?? "badge"}>
                      {LEVEL_LABEL[a.level] ?? a.level}
                    </span>
                  </td>
                  <td>
                    <strong>{a.patientId}</strong>
                  </td>
                  <td>{formatServerTime(a.triggeredAt)}</td>
                  <td>
                    <code>{a.ruleCode}</code>
                  </td>
                  <td>
                    <span className="hint">
                      {a.deliveries && a.deliveries.length > 0
                        ? a.deliveries
                            .map((d) => CHANNEL_LABEL[d.channel] ?? d.channel)
                            .join(" + ")
                        : "—"}
                    </span>
                  </td>
                  <td>
                    <span className={STATUS_CLASS[a.status] ?? "badge"}>
                      {STATUS_LABEL[a.status] ?? a.status}
                    </span>
                  </td>
                  <td>
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      {a.status === "open" ? (
                        <button
                          className="btn-secondary"
                          style={{ padding: "4px 12px", minHeight: 32, fontSize: 13 }}
                          disabled={pendingId === a.id}
                          onClick={() => void handleStatus(a, "acknowledged", "管理端确认收到")}
                        >
                          确认
                        </button>
                      ) : null}
                      {a.status === "open" || a.status === "acknowledged" ? (
                        <button
                          className="btn-primary"
                          style={{ padding: "4px 12px", minHeight: 32, fontSize: 13 }}
                          disabled={pendingId === a.id}
                          onClick={() => void handleStatus(a, "followed_up", "管理端登记随访")}
                        >
                          {pendingId === a.id ? "登记中…" : "登记随访"}
                        </button>
                      ) : null}
                      {a.status !== "closed" ? (
                        <button
                          className="btn-secondary"
                          style={{ padding: "4px 12px", minHeight: 32, fontSize: 13 }}
                          disabled={pendingId === a.id}
                          onClick={() => void handleStatus(a, "closed", "管理端关闭预警")}
                        >
                          关闭
                        </button>
                      ) : (
                        <span className="hint">已归档</span>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
