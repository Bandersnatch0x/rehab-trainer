import { STORAGE_KEYS } from '../../../utils/config';
import type { ExerciseView } from '../../../utils/patient-data';

Page({
  data: {
    loading: true,
    exercise: null as ExerciseView | null,
    missing: false,
  },
  onLoad() {
    let exercise: ExerciseView | undefined;
    try {
      exercise = wx.getStorageSync(STORAGE_KEYS.selectedExercise) as ExerciseView | undefined;
    } catch (error) {
      // 存储不可用时也按「没有选择训练条目」处理，避免页面永远停在加载中。
      exercise = undefined;
    }
    if (!exercise || !exercise.code) {
      this.setData({ loading: false, missing: true });
      return;
    }
    this.setData({ loading: false, exercise });
  },
  goBack() {
    wx.navigateBack();
  },
});