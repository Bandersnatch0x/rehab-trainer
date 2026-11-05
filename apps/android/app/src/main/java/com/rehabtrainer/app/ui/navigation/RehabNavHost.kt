package com.rehabtrainer.app.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rehabtrainer.app.ui.screens.AlertsScreen
import com.rehabtrainer.app.ui.screens.ExercisesScreen
import com.rehabtrainer.app.ui.screens.HomeScreen
import com.rehabtrainer.app.ui.screens.PatientsScreen
import com.rehabtrainer.app.ui.screens.RecordsScreen
import com.rehabtrainer.app.ui.screens.SettingsScreen
import com.rehabtrainer.app.ui.screens.TrainingScreen

/** 顶层路由表；页面均为骨架，业务内容在后续迭代填入。 */
@Composable
fun RehabNavHost(
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    // 当前选中的训练条目：决定训练页是否强制全身构图（D17：上肢类可用半身）。
    var selectedExercise by rememberSaveable { mutableStateOf("") }

    NavHost(
        navController = navController,
        startDestination = RehabDestination.HOME.route,
        modifier = modifier
    ) {
        composable(RehabDestination.HOME.route) {
            HomeScreen(
                widthSizeClass = widthSizeClass,
                onStartTraining = {
                    navController.navigate(RehabDestination.TRAINING.route) {
                        launchSingleTop = true
                    }
                },
                onOpenPrescription = {
                    navController.navigate(RehabDestination.EXERCISES.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(RehabDestination.EXERCISES.route) {
            ExercisesScreen(
                onSelectExercise = { code ->
                    selectedExercise = code
                    navController.navigate(RehabDestination.TRAINING.route)
                }
            )
        }
        composable(RehabDestination.PATIENTS.route) {
            PatientsScreen()
        }
        composable(RehabDestination.TRAINING.route) {
            TrainingScreen(
                widthSizeClass = widthSizeClass,
                exerciseCode = selectedExercise,
                // 紧急停止：退出训练页，回到上一个页面。
                onEmergencyStop = { navController.popBackStack() }
            )
        }
        composable(RehabDestination.RECORDS.route) {
            RecordsScreen()
        }
        composable(RehabDestination.ALERTS.route) {
            AlertsScreen()
        }
        composable(RehabDestination.SETTINGS.route) {
            SettingsScreen()
        }
    }
}
