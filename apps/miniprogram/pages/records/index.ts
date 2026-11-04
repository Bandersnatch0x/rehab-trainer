import { errorMessage, loadRecords, type RecordsData } from '../../utils/patient-data';

Page({
  data: { loading: false, error: '', result: null as RecordsData | null },
  onShow() { return this.loadData(); },
  async loadData() {
    if (this.data.loading) return;
    this.setData({ loading: true, error: '', result: null });
    try {
      this.setData({ result: await loadRecords() });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ loading: false });
    }
  },
});
