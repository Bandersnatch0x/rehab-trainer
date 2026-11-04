import { errorMessage, loadOverview, type OverviewData } from '../../utils/patient-data';

Page({
  data: { loading: false, error: '', overview: null as OverviewData | null },
  onShow() { return this.loadData(); },
  async loadData() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '', overview: null });
    try {
      this.setData({ overview: await loadOverview() });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
    }
  },
});
