import { PagePlaceholder } from "../components/PagePlaceholder";

export default function PermissionsPage() {
  return (
    <PagePlaceholder
      title="权限管理"
      description="待接入权限管理能力。契约当前未提供角色绑定或账号管理端点，需先补契约再实现。"
    >
      <p className="hint">
        权限范围限于当前机构；跨机构账号与数据不在此页暴露（D10）。
      </p>
    </PagePlaceholder>
  );
}
