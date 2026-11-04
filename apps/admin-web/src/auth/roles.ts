/**
 * Web 后台只承载两个机构侧角色。
 * 患者 / 照护者属于 App 与小程序，不进入管理端。
 */
export const ADMIN_ROLES = ["care_admin", "rehab_therapist"] as const;

export type AdminRole = (typeof ADMIN_ROLES)[number];

export const ROLE_LABELS: Record<AdminRole, string> = {
  care_admin: "照护管理员",
  rehab_therapist: "康复治疗师",
};

export function isAdminRole(value: unknown): value is AdminRole {
  return (
    typeof value === "string" &&
    (ADMIN_ROLES as readonly string[]).includes(value)
  );
}
