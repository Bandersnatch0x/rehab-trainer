import { useEffect, useState } from "react";
import { listAuditEvents } from "../api/operations";
import { formatServerTime, parseServerTime } from "../api/time";
import type { AuditEvent } from "../api/types";

const OUTCOME_LABEL: Record<string, string> = {
  success: "成功",
  denied: "已拒绝",
};

/** CSV 单元格转义：引号包裹并把内部引号翻倍。 */
function csvCell(value: string): string {
  return `"${value.replace(/"/g, '""')}"`;
}

export default function AuditLogPage() {
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [capped, setCapped] = useState(false);

  const [action, setAction] = useState("");
  const [resourceType, setResourceType] = useState("");
  const [actor, setActor] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    async function loadAll() {
      const collected: AuditEvent[] = [];
      const seen = new Set<string>();
      let cursor: string | undefined;
      do {
        const result = await listAuditEvents({
          limit: 200,
          ...(cursor ? { cursor } : {}),
        });
        if (cancelled) {
          return;
        }
        if (!result.ok) {
          setError(result.error.message);
          setEvents([]);
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

      setEvents(collected);
      setCapped(Boolean(cursor));
      setLoading(false);
    }

    void loadAll();

    return () => {
      cancelled = true;
    };
  }, []);

  const from = fromDate ? new Date(`${fromDate}T00:00:00`) : null;
  const to = toDate ? new Date(`${toDate}T23:59:59.999`) : null;

  const visible = events.filter((event) => {
    if (action.trim() && !event.action.includes(action.trim())) {
      return false;
    }
    if (resourceType.trim() && !(event.resourceType ?? "").includes(resourceType.trim())) {
      return false;
    }
    if (actor.trim() && !(event.actorAccountId ?? "").includes(actor.trim())) {
      return false;
    }
    const time = parseServerTime(event.createdAt);
    if (time) {
      if (from && time < from) return false;
      if (to && time > to) return false;
    }
    return true;
  });

  function exportCsv() {
    const header = ["时间", "操作人", "动作", "资源类型", "资源ID", "结果", "详情"];
    const rows = visible.map((event) => [
      formatServerTime(event.createdAt),
      event.actorAccountId ?? "",
      event.action,
      event.resourceType ?? "",
      event.resourceId ?? "",
      OUTCOME_LABEL[event.outcome] ?? event.outcome,
      JSON.stringify(event.detail ?? {}),
    ]);
    const csv = [header, ...rows]
      .map((row) => row.map((cell) => csvCell(String(cell))).join(","))
      .join("\r\n");
    const blob = new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `audit-log-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="page">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <h1>审计日志</h1>
          <p className="page-description">本机构操作审计事件，供处理明细核验与导出。</p>
        </div>
        <button
          className="btn-primary"
          onClick={exportCsv}
          disabled={loading || visible.length === 0}
        >
          导出 CSV
        </button>
      </div>

      <div className="notice">
        <div>
          <strong>审计范围：</strong>
          只显示当前登录机构的事件；跨机构访问被拒绝时记录在本机构名下。
          页面一次最多加载 1000 条，可用筛选缩小范围。
        </div>
      </div>

      <div className="card" style={{ padding: "16px 20px", display: "flex", gap: 16, alignItems: "flex-end", flexWrap: "wrap" }}>
        <label>
          <span>动作</span>
          <input value={action} onChange={(event) => setAction(event.target.value)} placeholder="如 patient.created" />
        </label>
        <label>
          <span>资源类型</span>
          <input value={resourceType} onChange={(event) => setResourceType(event.target.value)} placeholder="如 patient" />
        </label>
        <label>
          <span>操作人</span>
          <input value={actor} onChange={(event) => setActor(event.target.value)} placeholder="账号 ID" />
        </label>
        <label>
          <span>开始日期</span>
          <input type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} />
        </label>
        <label>
          <span>结束日期</span>
          <input type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} />
        </label>
        <span className="hint" style={{ marginLeft: "auto" }}>
          {loading
            ? "加载中…"
            : capped
              ? `已加载 ${events.length} 条（达到 1000 条上限，请用筛选缩小范围）`
              : `共 ${visible.length} / ${events.length} 条`}
        </span>
      </div>

      {error ? (
        <p role="alert" className="notice notice--danger">
          审计日志加载失败：{error}
        </p>
      ) : null}

      <div className="table-container">
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>操作人</th>
              <th>动作</th>
              <th>资源</th>
              <th>结果</th>
              <th>详情</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6}>正在读取审计日志…</td>
              </tr>
            ) : visible.length === 0 ? (
              <tr>
                <td colSpan={6}>当前筛选条件下没有审计事件。</td>
              </tr>
            ) : (
              visible.map((event) => (
                <tr key={event.id}>
                  <td>{formatServerTime(event.createdAt)}</td>
                  <td>{event.actorAccountId ?? "—"}</td>
                  <td>
                    <code>{event.action}</code>
                  </td>
                  <td>
                    <span className="hint">
                      {[event.resourceType, event.resourceId].filter(Boolean).join(" / ") || "—"}
                    </span>
                  </td>
                  <td>
                    <span
                      className={
                        event.outcome === "denied" ? "badge badge--danger" : "badge badge--success"
                      }
                    >
                      {OUTCOME_LABEL[event.outcome] ?? event.outcome}
                    </span>
                  </td>
                  <td>
                    <span className="hint">{JSON.stringify(event.detail ?? {})}</span>
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