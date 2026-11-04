import { PagePlaceholder } from "../components/PagePlaceholder";

export default function AlertsPage() {
  return (
    <PagePlaceholder
      title="预警中心"
      description="待接入 GET /alerts 与 PATCH /alerts/{alertId}：按级别与状态筛选，登记处理结果。"
    >
      <p className="hint">
        预警处理记录同时构成随访台账的数据来源。
      </p>
    </PagePlaceholder>
  );
}
