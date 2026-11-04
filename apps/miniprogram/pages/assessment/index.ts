import { errorMessage, loadPatientContext, submitAssessment, type PatientContext } from '../../utils/patient-data';

Page({
  data: {
    loading: false,
    error: '',
    context: null as PatientContext | null,
    feeling: '',
    submitting: false,
    submitError: '',
    submittedAt: '',
  },
  onShow() { return this.loadData(); },
  async loadData() {
    if (this.data.loading || this.data.submitting) return;
    this.setData({ loading: true, error: '', context: null, submitError: '', submittedAt: '' });
    try {
      this.setData({ context: await loadPatientContext() });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
    }
  },
  onFeelingInput(event: WechatMiniprogram.TextareaInput) {
    this.setData({ feeling: event.detail.value, submitError: '', submittedAt: '' });
  },
  async submit() {
    const patient = this.data.context?.patient;
    if (!patient || this.data.loading || this.data.submitting) return;
    this.setData({ submitting: true, submitError: '', submittedAt: '' });
    try {
      const submittedAt = await submitAssessment(patient.id, this.data.feeling);
      this.setData({ submittedAt, feeling: '' });
    } catch (error) {
      this.setData({ submitError: errorMessage(error) });
    } finally {
      this.setData({ submitting: false });
    }
  },
});
