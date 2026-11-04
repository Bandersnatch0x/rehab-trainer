package com.rehabtrainer.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rehabtrainer.app.ui.components.RehabTopBar
import com.rehabtrainer.app.ui.navigation.RehabDestination
import com.rehabtrainer.app.ui.navigation.RehabDrawerContent
import com.rehabtrainer.app.ui.navigation.RehabNavHost
import com.rehabtrainer.app.ui.navigation.RehabNavigationRail
import kotlinx.coroutines.launch

/**
 * 应用外壳。
 *
 * - Compact（手机）：抽屉导航 + 单列内容；抽屉条目整行且最小 80dp，避免细小的底部标签。
 * - Medium / Expanded（平板）：常驻侧栏；Expanded 时首页内容再分为双列，
 *   符合 D8「手机为主、平板兼容」。
 */
@Composable
fun RehabApp(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val title = RehabDestination.entries
        .firstOrNull { it.route == currentRoute }
        ?.label
        ?: "康复训练"

    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
        CompactShell(
            navController = navController,
            windowSizeClass = windowSizeClass,
            currentRoute = currentRoute,
            title = title,
            modifier = modifier
        )
    } else {
        ExpandedShell(
            navController = navController,
            windowSizeClass = windowSizeClass,
            currentRoute = currentRoute,
            title = title,
            modifier = modifier
        )
    }
}

@Composable
private fun CompactShell(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    currentRoute: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            RehabDrawerContent(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigateSingleTop(route)
                }
            )
        },
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                RehabTopBar(
                    title = title,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }
        ) { innerPadding ->
            RehabNavHost(
                navController = navController,
                widthSizeClass = windowSizeClass.widthSizeClass,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Composable
private fun ExpandedShell(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    currentRoute: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { RehabTopBar(title = title, onMenuClick = null) },
        modifier = modifier
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            RehabNavigationRail(
                currentRoute = currentRoute,
                onNavigate = { route -> navController.navigateSingleTop(route) }
            )
            RehabNavHost(
                navController = navController,
                widthSizeClass = windowSizeClass.widthSizeClass,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}
