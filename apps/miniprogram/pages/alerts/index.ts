import { isDemoMode } from '../../utils/auth';
import { errorState, loadAlerts, type AlertsData } from '../../utils/patient-data';

Page({
  data: {
    loading: false,
    error: '',
    needBinding: false,
    demoMode: false,
    result: null as AlertsData | null,
  },
  onShow() { return this.loadData(); },
  async loadData() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '', needBinding: false, demoMode: isDemoMode(), result: null });
    try {
      this.setData({ result: await loadAlerts() });
    } catch (error) {
      this.setData(errorState(error));
    } finally {
      this.setData({ loading: false });
    }
  },
  goBind() {
    wx.navigateTo({ url: '/pages/bind/index' });
  },
});
