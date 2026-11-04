import { useParams } from "react-router";
import { PagePlaceholder } from "../components/PagePlaceholder";

export default function StageConfirmationPage() {
  const { patientId } = useParams<{ patientId: string }>();

  return (
    <PagePlaceholder
      title="分期确认"
      description="待接入 POST /patients/{patientId}/stage-confirmations：仅康复治疗师可确认 H-Y 分期。"
    >
      <p className="hint">患者 ID：{patientId ?? "—"}</p>
      <p className="hint">
        未确认的分期标记为「疑似」，高风险训练条目不得准入（D11）。
      </p>
    </PagePlaceholder>
  );
}
