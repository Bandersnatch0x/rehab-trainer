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

/** 顶层路由表；连接各业务页面。 */
@Composable
fun RehabNavHost(
    navController: NavHostController,
    widthSizeClass: WindowWidthSizeClass,
    selectedExerciseCode: String = "EX-RAS-01",
    onSelectExercise: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = RehabDestination.HOME.route,
        modifier = modifier
    ) {
        composable(RehabDestination.HOME.route) {
            HomeScreen(
                widthSizeClass = widthSizeClass,
                onStartTraining = {
                    onSelectExercise("EX-RAS-01")
                    navController.navigate(RehabDestination.TRAINING.route)
                }
            )
        }
        composable(RehabDestination.EXERCISES.route) {
            ExercisesScreen(
                onSelectExercise = { code ->
                    onSelectExercise(code)
                    navController.navigate(RehabDestination.TRAINING.route)
                }
            )
        }
        composable(RehabDestination.PATIENTS.route) {
            PatientsScreen()
        }
        composable(RehabDestination.TRAINING.route) {
            TrainingScreen(
                exerciseCode = selectedExerciseCode,
                widthSizeClass = widthSizeClass
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
