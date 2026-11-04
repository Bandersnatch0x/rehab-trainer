/**
 * 后端用 `to_utc_naive` 存 UTC 并把时间序列化成不带时区的字符串
 * （例如 `2026-11-04T17:14:06`）。直接 `new Date(...)` 会按浏览器本地时区解释，
 * 在 UTC+8 会整体偏 8 小时。这里在缺少时区标记时补 `Z`。
 */
export function parseServerTime(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value);
  const date = new Date(hasZone ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** 展示用：按本地时区格式化到分钟。 */
export function formatServerTime(value: string | null | undefined): string {
  const date = parseServerTime(value);
  if (!date) {
    return "—";
  }
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

/** 是否仍然有效：没有到期时间视为有效。 */
export function isNotExpired(validUntil: string | null | undefined, now = Date.now()): boolean {
  const date = parseServerTime(validUntil);
  return date === null || date.getTime() >= now;
}
