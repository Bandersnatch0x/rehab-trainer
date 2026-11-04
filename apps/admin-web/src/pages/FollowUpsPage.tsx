import { PagePlaceholder } from "../components/PagePlaceholder";

export default function FollowUpsPage() {
  return (
    <PagePlaceholder
      title="随访台账"
      description="契约未提供独立的随访端点；台账由预警处理记录（PATCH /alerts/{alertId}）与训练记录汇总生成。"
    >
      <p className="hint">
        照护管理员登记随访，康复治疗师查看同一机构的处理记录。
      </p>
    </PagePlaceholder>
  );
}
