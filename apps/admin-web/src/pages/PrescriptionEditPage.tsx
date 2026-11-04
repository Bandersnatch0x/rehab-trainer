import { useParams } from "react-router";
import { PagePlaceholder } from "../components/PagePlaceholder";

export default function PrescriptionEditPage() {
  const { patientId } = useParams<{ patientId: string }>();

  return (
    <PagePlaceholder
      title="处方编辑"
      description="待接入 POST /prescriptions 与 PATCH /prescriptions/{prescriptionId}：仅康复治疗师可开具与调整。"
    >
      <p className="hint">患者 ID：{patientId ?? "—"}</p>
      <p className="hint">
        调整处方须携带 If-Match 版本号做乐观锁，冲突时提示刷新后重试（D12）。
      </p>
    </PagePlaceholder>
  );
}
