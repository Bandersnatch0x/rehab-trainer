import { useEffect, useState } from "react";
import { listAuditEvents } from "../api/operations";
import { formatServerTime } from "../api/time";
import type { AuditEvent } from "../api/types";

type AuditQuery = {
  action?: string;
  resourceType?: string;
  actorAccountId?: string;
  from?: string;
  to?: string;
};

const OUTCOME_LABEL: Record<string, string> = {
  success: "成功",
  denied: "已拒绝",
};

/** CSV 单元格转义：中和公式注入，再按引号包裹并把内部引号翻倍。 */
function csvCell(value: string): string {
  const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value;
  return `"${safe.replace(/"/g, '""')}"`;
}

/** 日期输入转 ISO；缺时区按浏览器本地时区解释。 */
function toIso(date: string, endOfDay: boolean): string | undefined {
  if (!date) {
    return undefined;
  }
  const value = new Date(`${date}T${endOfDay ? "23:59:59.999" : "00:00:00"}`);
  return Number.isNaN(value.getTime()) ? undefined : value.toISOString();
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
  // 筛选在服务端执行；未点「查询」前不改动已加载结果。
  const [query, setQuery] = useState<AuditQuery>({});

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
          limit: 100,
          ...query,
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
  }, [query]);

  function applyFilters() {
    const from = toIso(fromDate, false);
    const to = toIso(toDate, true);
    if (from && to && from > to) {
      setError("开始日期不能晚于结束日期");
      return;
    }
    setError(null);
    setQuery({
      ...(action.trim() ? { action: action.trim() } : {}),
      ...(resourceType.trim() ? { resourceType: resourceType.trim() } : {}),
      ...(actor.trim() ? { actorAccountId: actor.trim() } : {}),
      ...(from ? { from } : {}),
      ...(to ? { to } : {}),
    });
  }

  function resetFilters() {
    setAction("");
    setResourceType("");
    setActor("");
    setFromDate("");
    setToDate("");
    setQuery({});
  }

  const visible = events;

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
    // 游离节点上的 click 在部分浏览器不会触发下载；revoke 也要等下载启动后。
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 0);
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
          disabled={loading || capped || visible.length === 0}
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
        <button className="btn-primary" onClick={applyFilters} disabled={loading}>
          查询
        </button>
        <button className="btn-secondary" onClick={resetFilters} disabled={loading}>
          重置
        </button>
        <span className="hint" style={{ marginLeft: "auto" }}>
          {loading
            ? "加载中…"
            : capped
              ? `已加载 ${events.length} 条（达到 1000 条上限，请缩小筛选范围后重新查询；此时禁止导出）`
              : `共 ${visible.length} 条`}
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