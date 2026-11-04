package com.rehabtrainer.app.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
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
    NavHost(
        navController = navController,
        startDestination = RehabDestination.HOME.route,
        modifier = modifier
    ) {
        composable(RehabDestination.HOME.route) {
            HomeScreen(widthSizeClass = widthSizeClass)
        }
        composable(RehabDestination.EXERCISES.route) {
            ExercisesScreen(onSelectExercise = { navController.navigate(RehabDestination.TRAINING.route) })
        }
        composable(RehabDestination.PATIENTS.route) {
            PatientsScreen()
        }
        composable(RehabDestination.TRAINING.route) {
            TrainingScreen()
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
