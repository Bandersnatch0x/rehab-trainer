import { listPrescriptions } from "./operations";
import { isNotExpired } from "./time";
import type { ApiError } from "./client";
import type { Prescription } from "./types";

export type CurrentPrescriptionResult =
  | { ok: true; data: Prescription | null }
  | { ok: false; error: ApiError };

/**
 * 找到"当前有效处方"：状态 active 且未过期，按签发时间倒序取第一条。
 *
 * 契约按签发时间倒序返回，但默认每页 20 条；历史处方多时有效的那条可能不在第一页，
 * 所以这里沿 cursor 翻页查找，而不是只看 items[0]。
 */
export async function findCurrentPrescription(
  patientId: string,
): Promise<CurrentPrescriptionResult> {
  let cursor: string | undefined;
  let scanned = 0;

  do {
    const result = await listPrescriptions({
      patientId,
      limit: 100,
      ...(cursor ? { cursor } : {}),
    });
    if (!result.ok) {
      return result;
    }

    const found = result.data.items.find(
      (item) => item.status === "active" && isNotExpired(item.validUntil),
    );
    if (found) {
      return { ok: true, data: found };
    }

    scanned += result.data.items.length;
    const nextCursor = result.data.nextCursor ?? undefined;
    // 游标没有前进说明服务端异常；继续翻页只会死循环。
    cursor = nextCursor === cursor ? undefined : nextCursor;
  } while (cursor && scanned < 1000);

  return { ok: true, data: null };
}
