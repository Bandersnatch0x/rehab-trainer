import { STORAGE_KEYS } from '../../../utils/config';
import type { ExerciseView } from '../../../utils/patient-data';

Page({
  data: {
    loading: true,
    exercise: null as ExerciseView | null,
    missing: false,
  },
  onLoad() {
    const exercise = wx.getStorageSync(STORAGE_KEYS.selectedExercise) as ExerciseView | undefined;
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