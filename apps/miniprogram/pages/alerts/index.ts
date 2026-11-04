import { errorMessage, loadAlerts, type AlertsData } from '../../utils/patient-data';

Page({
  data: { loading: false, error: '', result: null as AlertsData | null },
  onShow() { return this.loadData(); },
  async loadData() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '', result: null });
    try {
      this.setData({ result: await loadAlerts() });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
    }
  },
});
