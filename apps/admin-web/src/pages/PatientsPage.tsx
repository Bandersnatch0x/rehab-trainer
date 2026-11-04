import { PagePlaceholder } from "../components/PagePlaceholder";

export default function PatientsPage() {
  return (
    <PagePlaceholder
      title="患者列表"
      description="待接入 GET /patients：列表、按分期与状态筛选、游标分页。"
    >
      <p className="hint">
        多机构隔离：仅展示当前登录机构下的患者，界面不提供跨机构入口。
      </p>
    </PagePlaceholder>
  );
}
