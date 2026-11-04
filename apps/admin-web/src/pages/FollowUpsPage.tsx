import { useEffect, useState } from "react";
import { listAlerts } from "../api/operations";
import { formatServerTime } from "../api/time";
import type { Alert } from "../api/types";

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
  followed_up: "已随访",
  closed: "已关闭",
};

const CHANNEL_LABEL: Record<string, string> = {
  in_app_popup: "端上弹窗",
  voice_prompt: "语音提示",
  push_notification: "推送通知",
  audit_log: "审计日志",
};

const DELIVERY_LABEL: Record<string, string> = {
  pending: "待发送",
  sent: "已发送",
  failed: "发送失败",
  fallback: "已转备用",
};


export default function FollowUpsPage() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    async function loadAll() {
      const collected: Alert[] = [];
      const seen = new Set<string>();
      let cursor: string | undefined;
      do {
        const result = await listAlerts({
          limit: 100,
          ...(cursor ? { cursor } : {}),
        });
        if (cancelled) {
          return;
        }
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

      // 台账只收已处置的预警；契约没有单独的随访记录端点。
      setAlerts(
        collected.filter(
          (item) => item.status === "followed_up" || item.status === "closed",
        ),
      );
      setLoading(false);
    }

    void loadAll();

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="page">
      <h1>随访台账</h1>
      <p className="page-description">
        汇总预警处置结果与患者训练依从性跟踪，供照护管理员与康复治疗师核验。
      </p>

      <div className="notice">
        <div>
          <strong>台账口径：</strong>
          数据来自预警处置记录（PATCH /alerts/&#123;id&#125;）中状态为「已随访」或「已关闭」的条目。
          契约未提供处理人与结论字段，处理明细需从审计日志导出，本页不臆造。
        </div>
      </div>

      {error ? (
        <p role="alert" className="notice notice--danger">
          随访台账加载失败：{error}
        </p>
      ) : null}

      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>台账编号</th>
              <th>患者</th>
              <th>触发时间</th>
              <th>触发规则</th>
              <th>级别</th>
              <th>通知进展</th>
              <th>处置状态</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={7}>正在读取随访台账…</td>
              </tr>
            ) : alerts.length === 0 ? (
              <tr>
                <td colSpan={7}>暂无已处置的预警记录。</td>
              </tr>
            ) : (
              alerts.map((alert) => (
                <tr key={alert.id}>
                  <td>
                    <strong>{alert.id}</strong>
                  </td>
                  <td>{alert.patientId}</td>
                  <td>{formatServerTime(alert.triggeredAt)}</td>
                  <td>
                    <code>{alert.ruleCode}</code>
                  </td>
                  <td>
                    <span className={LEVEL_CLASS[alert.level] ?? "badge"}>
                      {LEVEL_LABEL[alert.level] ?? alert.level}
                    </span>
                  </td>
                  <td>
                    <span className="hint">
                      {alert.deliveries && alert.deliveries.length > 0
                        ? alert.deliveries
                            .map(
                              (delivery) =>
                                `${CHANNEL_LABEL[delivery.channel] ?? delivery.channel}：${
                                  DELIVERY_LABEL[delivery.status] ?? delivery.status
                                }`,
                            )
                            .join("；")
                        : "—"}
                    </span>
                  </td>
                  <td>
                    <span className="badge badge--success">
                      {STATUS_LABEL[alert.status] ?? alert.status}
                    </span>
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
