import { isDemoMode } from '../../utils/auth';
import { errorState, loadOverview, type OverviewData } from '../../utils/patient-data';

Page({
  data: {
    loading: false,
    error: '',
    needBinding: false,
    demoMode: false,
    overview: null as OverviewData | null,
  },
  onShow() { return this.loadData(); },
  async loadData() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '', needBinding: false, demoMode: isDemoMode(), overview: null });
    try {
      this.setData({ overview: await loadOverview() });
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
