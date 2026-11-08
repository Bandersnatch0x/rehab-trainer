package com.rehabtrainer.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.core.pose.ExerciseKind
import com.rehabtrainer.app.data.session.SessionRepository
import com.rehabtrainer.app.data.session.SessionSnapshot
import com.rehabtrainer.app.ui.components.RehabTopBar
import com.rehabtrainer.app.ui.screens.BindScreen
import com.rehabtrainer.app.ui.navigation.RehabDestination
import com.rehabtrainer.app.ui.navigation.RehabDrawerContent
import com.rehabtrainer.app.ui.navigation.RehabNavHost
import com.rehabtrainer.app.ui.navigation.RehabNavigationRail
import kotlinx.coroutines.launch

/**
 * 训练动作元数据定义，用于顶栏、训练页与组状态机联动。
 */
data class ExerciseMeta(
    val code: String,
    val name: String,
    val shortName: String,
    val target: String,
    val hasRas: Boolean,
    val bpm: Int = 100,
    // 文献预设（待临床签核）：无 10 米步行基线时 100 步/分（90–110）；
    // 有基线后原地踏步按 110% 基线个体化（Chang 2019），详见 docs/clinical/literature-presets.md。
    val videoTitle: String,
    val prompt: String,
    val sets: Int,
    val repsPerSet: Int,
    val requiresFullBody: Boolean,
    val restSeconds: Int = 30,
    // 计数标签必须如实说明在数什么；姿势矫正尚未接入分析器（P1-16）。
    val counterLabel: String = "抬举次数",
    // 规则法动作分析器类型（踏步 / 微蹲 / 抬举）。
    val kind: ExerciseKind = ExerciseKind.ARM_RAISE,
    // 服务端下发的示范视频；为空时回退 APK 内置演示视频。
    val videoUri: String? = null,
    val watermarkText: String? = null,
    val creditLine: String? = null,
    val riskLevel: String? = null,
    // 变体指定的支撑等级（如「坐位」「站立 · 可扶支撑」）；无变体时为空。
    val supportLevelText: String? = null
)

/** 首页「开始今日训练」与训练列表共用同一训练条目代码。 */
const val TODAY_TRAINING_CODE = "EX-RAS-01"

val DEFAULT_EXERCISES = listOf(
    ExerciseMeta(
        code = "EX-RAS-01",
        name = "下肢原地节律踏步",
        shortName = "原地踏步",
        target = "目标 12 次 · 100 BPM",
        hasRas = true,
        bpm = 100,
        videoTitle = "原地节律踏步 · 动作演示",
        prompt = "“保持挺胸直视前方，大腿抬高至水平，落地轻柔跟随 100 BPM 节奏”",
        sets = 3,
        repsPerSet = 12,
        requiresFullBody = true,
        counterLabel = "踏步次数",
        kind = ExerciseKind.MARCH,
    ),
    ExerciseMeta(
        code = "EX-STRETCH-01",
        name = "坐位双上肢躯干伸展",
        shortName = "躯干伸展",
        target = "目标 10 次 · 自主节律",
        hasRas = false,
        videoTitle = "躯干牵伸放松 · 动作演示",
        prompt = "“双臂自然上举过头顶，躯干轻柔向两侧延伸，深呼吸保持均匀”",
        sets = 2,
        repsPerSet = 10,
        requiresFullBody = false,
        counterLabel = "伸展保持次数",
        kind = ExerciseKind.STRETCH,
    ),
    ExerciseMeta(
        code = "EX-STRENGTH-01",
        name = "下肢靠墙微蹲训练",
        shortName = "靠墙微蹲",
        target = "目标 8 次 · 稳固扶手",
        hasRas = false,
        videoTitle = "下肢靠墙微蹲 · 动作演示",
        prompt = "“背部贴墙缓缓下蹲至膝屈 30°，双手扶稳把手，起身平稳”",
        sets = 2,
        repsPerSet = 8,
        requiresFullBody = true,
        counterLabel = "蹲起次数",
        kind = ExerciseKind.SQUAT,
    ),
    ExerciseMeta(
        code = "EX-POSTURE-01",
        name = "站立位脊柱对齐矫正",
        shortName = "姿势矫正",
        target = "目标 10 次 · 实时矫正",
        hasRas = false,
        videoTitle = "站立脊柱矫正 · 动作演示",
        prompt = "“双肩下沉放松，收腹挺胸，感受头顶向上延伸对齐重力垂线”",
        sets = 3,
        repsPerSet = 10,
        requiresFullBody = true,
        counterLabel = "保持次数",
        kind = ExerciseKind.POSTURE,
    )
)

/**
 * 应用外壳。
 *
 * - P0-1 修复：平板判定使用 [smallestScreenWidthDp] >= 600 (即 sw600dp)。
 *   手机（sw < 600dp）无论横屏还是竖屏一律使用 CompactShell（带 Drawer 抽屉导航），
 *   杜绝横屏手机被误判为平板而强行加载常驻 NavigationRail 导致溢出与内容压缩。
 * - Medium / Expanded（真正平板 sw >= 600dp）：使用 ExpandedShell 常驻侧栏。
 */
@Composable
fun RehabApp(
    windowSizeClass: WindowSizeClass,
    sessionRepository: SessionRepository? = null,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf(sessionRepository?.cachedSnapshot()) }
    // 没有令牌就必须重新绑定；不能靠残留快照把用户留在旧患者数据里。
    var needBind by remember {
        mutableStateOf(sessionRepository != null && !sessionRepository.hasSession())
    }
    var demoMode by rememberSaveable { mutableStateOf(false) }
    var bindBusy by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionRepository) {
        val repo = sessionRepository ?: return@LaunchedEffect
        if (!repo.hasSession()) return@LaunchedEffect
        bindBusy = true
        when (val result = repo.refresh()) {
            is ApiResult.Success -> {
                snapshot = result.value
                bindError = null
            }
            is ApiResult.Failure -> if (snapshot == null) bindError = result.error.message
        }
        bindBusy = false
    }

    LaunchedEffect(snapshot) {
        snapshot?.let {
            ExerciseRegistry.updateFromPlan(it.plan, it.patientId, it.prescriptionId)
        }
    }

    val acceptSnapshot: (com.rehabtrainer.app.data.session.SessionSnapshot) -> Unit = {
        snapshot = it
        needBind = false
        demoMode = false
        bindError = null
    }

    if (sessionRepository != null && !demoMode && (needBind || snapshot == null)) {
        BindScreen(
            busy = bindBusy,
            error = bindError,
            onBind = { code ->
                scope.launch {
                    bindBusy = true
                    bindError = null
                    when (val result = sessionRepository.bind(code, null)) {
                        is ApiResult.Success -> acceptSnapshot(result.value)
                        is ApiResult.Failure -> bindError = result.error.message
                    }
                    bindBusy = false
                }
            },
            onLogin = { username, password ->
                scope.launch {
                    bindBusy = true
                    bindError = null
                    when (val result = sessionRepository.login(username, password, null)) {
                        is ApiResult.Success -> acceptSnapshot(result.value)
                        is ApiResult.Failure -> bindError = result.error.message
                    }
                    bindBusy = false
                }
            },
            onSkipDemo = {
                demoMode = true
                ExerciseRegistry.reset()
            },
            modifier = modifier
        )
        return
    }

    var selectedExerciseCode by rememberSaveable {
        mutableStateOf(ExerciseRegistry.exercises.first().code)
    }
    LaunchedEffect(snapshot) {
        if (ExerciseRegistry.exercises.none { it.code == selectedExerciseCode }) {
            selectedExerciseCode = ExerciseRegistry.exercises.first().code
        }
    }
    val currentExercise = ExerciseRegistry.exercises.firstOrNull { it.code == selectedExerciseCode }
        ?: ExerciseRegistry.exercises.first()

    val logout: () -> Unit = {
        sessionRepository?.logout()
        ExerciseRegistry.reset()
        snapshot = null
        demoMode = false
        needBind = sessionRepository != null
    }

    val title = when (currentRoute) {
        RehabDestination.TRAINING.route -> "训练执行 · ${currentExercise.shortName}"
        else -> RehabDestination.entries.firstOrNull { it.route == currentRoute }?.label ?: "康复训练"
    }

    if (!isTablet) {
        CompactShell(
            navController = navController,
            windowSizeClass = windowSizeClass,
            currentRoute = currentRoute,
            title = title,
            snapshot = snapshot,
            selectedExerciseCode = selectedExerciseCode,
            onSelectExercise = { selectedExerciseCode = it },
            onLogout = logout,
            modifier = modifier
        )
    } else {
        ExpandedShell(
            navController = navController,
            windowSizeClass = windowSizeClass,
            currentRoute = currentRoute,
            title = title,
            snapshot = snapshot,
            selectedExerciseCode = selectedExerciseCode,
            onSelectExercise = { selectedExerciseCode = it },
            onLogout = logout,
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
    snapshot: SessionSnapshot?,
    selectedExerciseCode: String,
    onSelectExercise: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isTraining = currentRoute == RehabDestination.TRAINING.route

    if (isTraining) {
        // 沉浸模式：训练页不渲染顶栏与抽屉，避免误触离开；返回键与紧急停止仍可退出。
        RehabNavHost(
            navController = navController,
            widthSizeClass = windowSizeClass.widthSizeClass,
            selectedExerciseCode = selectedExerciseCode,
            snapshot = snapshot,
            onSelectExercise = onSelectExercise,
            onLogout = onLogout,
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        )
        return
    }

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
                selectedExerciseCode = selectedExerciseCode,
                snapshot = snapshot,
                onSelectExercise = onSelectExercise,
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
    snapshot: SessionSnapshot?,
    selectedExerciseCode: String,
    onSelectExercise: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTraining = currentRoute == RehabDestination.TRAINING.route

    if (isTraining) {
        // 沉浸模式：训练页隐藏顶栏与常驻侧栏，把宽度全部留给双播放/实景。
        RehabNavHost(
            navController = navController,
            widthSizeClass = windowSizeClass.widthSizeClass,
            selectedExerciseCode = selectedExerciseCode,
            snapshot = snapshot,
            onSelectExercise = onSelectExercise,
            onLogout = onLogout,
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        )
        return
    }

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
                selectedExerciseCode = selectedExerciseCode,
                snapshot = snapshot,
                onSelectExercise = onSelectExercise,
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
