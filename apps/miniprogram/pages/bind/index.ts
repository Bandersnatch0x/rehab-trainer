import { bindWithCode, loginWithPassword, setDemoMode } from '../../utils/auth';
import { DEMO_ACCOUNT } from '../../utils/config';
import { errorMessage } from '../../utils/patient-data';

Page({
  data: {
    code: '',
    submitting: false,
    error: '',
    demoLoading: false,
    demoError: '',
  },
  onCodeInput(event: WechatMiniprogram.Input) {
    this.setData({ code: event.detail.value, error: '' });
  },
  async submit() {
    const code = this.data.code.trim();
    if (!code || this.data.submitting) return;
    this.setData({ submitting: true, error: '' });
    try {
      await bindWithCode({ bindingToken: code });
      setDemoMode(false);
      wx.switchTab({ url: '/pages/index/index' });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ submitting: false });
    }
  },
  async enterDemo() {
    if (this.data.demoLoading || this.data.submitting) return;
    this.setData({ demoLoading: true, demoError: '' });
    try {
      await loginWithPassword(DEMO_ACCOUNT.username, DEMO_ACCOUNT.password);
      setDemoMode(true);
      wx.switchTab({ url: '/pages/index/index' });
    } catch (error) {
      this.setData({ demoError: errorMessage(error) });
    } finally {
      this.setData({ demoLoading: false });
    }
  },
});
