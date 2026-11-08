import type { AdminRole } from "../auth/roles";

export interface NavItem {
  to: string;
  label: string;
  roles: readonly AdminRole[];
}

/**
 * 顶层菜单的唯一配置源。
 * 分期确认与处方编辑是患者上下文操作，不设顶层入口，
 * 由患者详情页按角色展示（见 PatientDetailPage）。
 */
export const NAV_ITEMS: readonly NavItem[] = [
  {
    to: "/patients",
    label: "患者列表",
    roles: ["care_admin", "rehab_therapist"],
  },
  {
    to: "/alerts",
    label: "预警中心",
    roles: ["care_admin", "rehab_therapist"],
  },
  {
    to: "/follow-ups",
    label: "随访台账",
    roles: ["care_admin", "rehab_therapist"],
  },
  {
    to: "/audit-log",
    label: "审计日志",
    roles: ["care_admin", "rehab_therapist"],
  },
  {
    to: "/research",
    label: "科研数据",
    roles: ["care_admin", "rehab_therapist"],
  },
  {
    to: "/media-assets",
    label: "训练视频",
    roles: ["care_admin", "rehab_therapist"],
  },
  {
    to: "/admin/permissions",
    label: "权限管理",
    // 两类角色都能进：治疗师只能授予/调整治疗师，服务端另有越权拦截。
    roles: ["care_admin", "rehab_therapist"],
  },
  {
    to: "/account/security",
    label: "账号安全",
    roles: ["care_admin", "rehab_therapist"],
  },
];

export function canAccess(role: AdminRole | null, item: NavItem): boolean {
  return role !== null && item.roles.includes(role);
}
