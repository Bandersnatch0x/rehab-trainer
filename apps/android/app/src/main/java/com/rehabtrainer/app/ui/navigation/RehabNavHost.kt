package com.rehabtrainer.app.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rehabtrainer.app.data.session.SessionSnapshot
import com.rehabtrainer.app.ui.ExerciseRegistry
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
    selectedExerciseCode: String? = null,
    snapshot: SessionSnapshot? = null,
    onSelectExercise: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val trainingCode = selectedExerciseCode ?: ExerciseRegistry.exercises.first().code
    NavHost(
        navController = navController,
        startDestination = RehabDestination.HOME.route,
        modifier = modifier
    ) {
        composable(RehabDestination.HOME.route) {
            HomeScreen(
                widthSizeClass = widthSizeClass,
                snapshot = snapshot,
                onStartTraining = {
                    onSelectExercise(trainingCode)
                    navController.navigate(RehabDestination.TRAINING.route)
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
                    onSelectExercise(code)
                    navController.navigate(RehabDestination.TRAINING.route)
                }
            )
        }
        composable(RehabDestination.PATIENTS.route) {
            PatientsScreen(orgName = snapshot?.orgName)
        }
        composable(RehabDestination.TRAINING.route) {
            TrainingScreen(
                exerciseCode = trainingCode,
                widthSizeClass = widthSizeClass,
                // 紧急停止：退出训练页，回到上一个页面。
                onEmergencyStop = { navController.popBackStack() }
            )
        }
        composable(RehabDestination.RECORDS.route) {
            RecordsScreen()
        }
        composable(RehabDestination.ALERTS.route) {
            AlertsScreen(patientId = snapshot?.patientId)
        }
        composable(RehabDestination.SETTINGS.route) {
            SettingsScreen(onLogout = onLogout)
        }
    }
}
