import { isDemoMode, isLoggedIn } from '../../utils/auth';
import { STORAGE_KEYS } from '../../utils/config';
import { errorState, exerciseView, loadExercises, type ExerciseView } from '../../utils/patient-data';
import type { components } from '../../types/api';

const SELECTED_EXERCISE_KEY = STORAGE_KEYS.selectedExercise;

type Exercise = components['schemas']['Exercise'];

/** 未登录时展示的演示条目，名称与分级取自 seed 演示数据（D21）。 */
const DEMO_EXERCISES: Exercise[] = [
  {
    code: 'demo-stretch',
    label: '肩颈牵伸',
    category: 'stretching-relaxation',
    publicationStatus: 'published',
    riskLevel: 'low',
    applicableStages: [1, 2, 3, 4],
    defaultDosage: { sets: 2, repetitions: 8, durationMinutes: 5 },
  },
  {
    code: 'demo-strength',
    label: '坐位抬腿',
    category: 'strength-maintenance',
    publicationStatus: 'published',
    riskLevel: 'medium',
    applicableStages: [1, 2, 3, 4],
    defaultDosage: { sets: 3, repetitions: 10 },
  },
  {
    code: 'demo-gait',
    label: '节拍步行',
    category: 'rhythmic-gait',
    publicationStatus: 'published',
    riskLevel: 'high',
    applicableStages: [1, 2, 3, 4],
    defaultDosage: { durationMinutes: 10 },
  },
];

function demoExercises(unlocked: boolean): ExerciseView[] {
  return DEMO_EXERCISES.map((exercise) => exerciseView(exercise, unlocked));
}

Page({
  data: {
    loading: false,
    error: '',
    needBinding: false,
    demoData: true,
    demoUnlocked: false,
    exercises: demoExercises(false),
  },
  onShow() { return this.loadData(); },
  async loadData() {
    if (this.data.loading) return;
    if (!isLoggedIn()) {
      this.setData({
        loading: false,
        error: '',
        needBinding: false,
        demoData: true,
        exercises: demoExercises(this.data.demoUnlocked),
      });
      return;
    }
    this.setData({
      loading: true,
      error: '',
      needBinding: false,
      demoData: isDemoMode(),
    });
    try {
      this.setData({ exercises: await loadExercises(this.data.demoUnlocked) });
    } catch (error) {
      this.setData(errorState(error));
    } finally {
      this.setData({ loading: false });
    }
  },
  onDemoUnlockChange(event: WechatMiniprogram.SwitchChange) {
    const demoUnlocked = event.detail.value;
    this.setData({ demoUnlocked });
    if (!isLoggedIn()) {
      this.setData({ exercises: demoExercises(demoUnlocked) });
      return;
    }
    this.loadData();
  },
  /** 记录选中条目再跳转；执行页不重复请求，只渲染这一条。 */
  onStartExercise(event: WechatMiniprogram.BaseEvent<Record<string, never>, { index: number }>) {
    const index = Number(event.currentTarget.dataset.index);
    const selected = this.data.exercises[index];
    if (!selected) return;
    wx.setStorageSync(SELECTED_EXERCISE_KEY, selected);
    wx.navigateTo({ url: '/pages/training/session/index' });
  },
  goBind() {
    wx.navigateTo({ url: '/pages/bind/index' });
  },
});
