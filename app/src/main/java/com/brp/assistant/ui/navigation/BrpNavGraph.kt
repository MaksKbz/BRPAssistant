package com.brp.assistant.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.*
import androidx.navigation.compose.*
import com.brp.assistant.ui.MainViewModel
import com.brp.assistant.ui.accessory.AccessoryShopScreen
import com.brp.assistant.ui.chat.ChatScreen
import com.brp.assistant.ui.chat.ChatViewModel
import com.brp.assistant.ui.compare.CompareScreen
import com.brp.assistant.ui.diagnose.DiagnoseScreen
import com.brp.assistant.ui.home.HomeScreen
import com.brp.assistant.ui.model.ModelManagerScreen
import com.brp.assistant.ui.model.ModelManagerViewModel
import com.brp.assistant.ui.situations.SituationsScreen
import com.brp.assistant.ui.situations.SituationsViewModel
import com.brp.assistant.ui.maintenance.MaintenanceScreen
import com.brp.assistant.ui.maintenance.MaintenanceViewModel
import com.brp.assistant.ui.vehicle.VehicleSelectScreen
import com.brp.assistant.ui.vehicle.VehicleSelectViewModel

sealed class Screen(val route: String, val label: String) {
    data object ModelManager  : Screen("model-manager",  "Настройки ИИ")
    data object Models        : Screen("model-manager",  "Модели")
    data object Home          : Screen("home",           "Главная")
    data object Situations    : Screen("situations",     "Инструкции")
    data object Maintenance   : Screen("maintenance",    "Регламент")
    data object VehicleSelect : Screen("vehicle-select", "Моя техника")
    data object Chat          : Screen("chat/{mode}?q={q}", "Чат") {
        fun createRoute(mode: String, query: String? = null): String {
            val eq = query?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()) }
            return "chat/$mode" + (if (eq != null) "?q=$eq" else "")
        }
    }
    data object Diagnose      : Screen("diagnose",  "Диагностика")
    data object AccessoryShop : Screen("accessory", "Аксессуары")
    data object Compare       : Screen("compare",   "Сравнение")
}

private val bottomScreens = listOf(
    Screen.Home, Screen.Diagnose, Screen.AccessoryShop, Screen.VehicleSelect
)

// ─────────────────────────────────────────────────────────────────────────────
// NavItem: иконка + экран, используется и в BottomBar, и в NavigationRail
// ─────────────────────────────────────────────────────────────────────────────
private data class NavItem(
    val screen: Screen,
    val iconContent: @Composable () -> Unit
)

private val navItems = listOf(
    NavItem(Screen.Home)          { Icon(Icons.Default.Home,          contentDescription = Screen.Home.label) },
    NavItem(Screen.Diagnose)      { Icon(Icons.Default.Build,         contentDescription = Screen.Diagnose.label) },
    NavItem(Screen.AccessoryShop) { Icon(Icons.Default.ShoppingBag,   contentDescription = Screen.AccessoryShop.label) },
    NavItem(Screen.VehicleSelect) { Icon(Icons.Default.DirectionsCar, contentDescription = Screen.VehicleSelect.label) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrpNavGraph(
    navController: NavHostController     = rememberNavController(),
    mainViewModel: MainViewModel         = hiltViewModel(),
    /**
     * Передаётся из MainActivity через calculateWindowSizeClass().
     * Compact  → телефон  → BottomNavigationBar
     * Medium / Expanded → планшет/фолдабл → NavigationRail
     */
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route

    val showNav = currentRoute in bottomScreens.map { it.route } ||
            currentRoute?.startsWith("chat/") == true

    // Compact  = телефон  (<600dp)
    // Medium   = планшет в портретном / фолдабл разложенный (600..840dp)
    // Expanded = планшет в ландшафте / Fold (>840dp)
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    val selectedVehicleId   by mainViewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val selectedVehicleName by mainViewModel.selectedVehicleName.collectAsStateWithLifecycle()
    val selectedVehicle     by mainViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val activeModelName     by mainViewModel.activeModelName.collectAsStateWithLifecycle()
    val currentTheme        by mainViewModel.appTheme.collectAsStateWithLifecycle()

    if (useRail && showNav) {
        // ── Планшет / фолдабл: NavigationRail слева ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                header = {
                    // Лого BRP в шапке рейла
                    Icon(
                        imageVector = Icons.Default.TwoWheeler,
                        contentDescription = "BRP",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 16.dp, bottom = 8.dp)
                            .size(28.dp)
                    )
                }
            ) {
                Spacer(Modifier.weight(1f))
                navItems.forEach { item ->
                    val selected = currentRoute?.startsWith(item.screen.route) == true
                    NavigationRailItem(
                        icon     = item.iconContent,
                        label    = { Text(item.screen.label) },
                        selected = selected,
                        onClick  = {
                            navController.navigate(item.screen.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            NavHostContent(
                navController       = navController,
                mainViewModel       = mainViewModel,
                selectedVehicleId   = selectedVehicleId,
                selectedVehicleName = selectedVehicleName,
                selectedVehicle     = selectedVehicle,
                activeModelName     = activeModelName,
                currentTheme        = currentTheme,
                widthSizeClass      = widthSizeClass,
                modifier            = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    } else {
        // ── Телефон: BottomNavigationBar ───────────────────────────────────────────────
        Scaffold(
            // FIX: safeDrawing insets — BottomNavBar не перекрывается системной
            // полосой жестовой навигации на Android 10+ gesture mode
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showNav) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = currentRoute?.startsWith(item.screen.route) == true
                            NavigationBarItem(
                                icon     = item.iconContent,
                                label    = { Text(item.screen.label) },
                                selected = selected,
                                onClick  = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHostContent(
                navController       = navController,
                mainViewModel       = mainViewModel,
                selectedVehicleId   = selectedVehicleId,
                selectedVehicleName = selectedVehicleName,
                selectedVehicle     = selectedVehicle,
                activeModelName     = activeModelName,
                currentTheme        = currentTheme,
                widthSizeClass      = widthSizeClass,
                modifier            = Modifier.padding(innerPadding)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NavHostContent ─ отдельный композабл, чтобы не дублировать NavHost
// между ветками Rail (планшет) и BottomBar (телефон)
// FIX: добавлен widthSizeClass параметр — теперь передаётся в HomeScreen,
// чтобы планшетный 2-колоночный layout и QuickDiagnoseStrip реально активировались.
// Раньше widthSizeClass не проходил дальше BrpNavGraph.
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavHostContent(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    selectedVehicleId: String?,
    selectedVehicleName: String?,
    selectedVehicle: Any?,
    activeModelName: String?,
    currentTheme: String,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Home.route,
        modifier         = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                selectedVehicleName = selectedVehicleName,
                activeModelName     = activeModelName,
                currentTheme        = currentTheme,
                // FIX: теперь widthSizeClass действительно передаётся в HomeScreen
                widthSizeClass      = widthSizeClass,
                onSetTheme          = { mainViewModel.setAppTheme(it) },
                onNavigate          = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.Situations.route) {
            val situVm: SituationsViewModel = hiltViewModel()
            val state by situVm.state.collectAsStateWithLifecycle()
            SituationsScreen(
                categories       = state.categories,
                nodes            = state.nodes,
                cards            = state.cards,
                selectedCategory = state.selectedCategory,
                selectedNode     = state.selectedNode,
                selectedVehicle  = selectedVehicle,
                onCategorySelect = { situVm.selectCategory(it) },
                onNodeSelect     = { node, isEl -> situVm.selectNode(node, isEl) },
                onBack           = { navController.popBackStack() }
            )
        }
        composable(Screen.Maintenance.route) {
            val maintVm: MaintenanceViewModel = hiltViewModel()
            val state by maintVm.state.collectAsStateWithLifecycle()
            MaintenanceScreen(
                selectedVehicle      = selectedVehicle,
                purchaseDate         = state.purchaseDate,
                onUpdatePurchaseDate = { maintVm.updatePurchaseDate(it) },
                onBack               = { navController.popBackStack() }
            )
        }
        composable(Screen.VehicleSelect.route) {
            val vehicleVm: VehicleSelectViewModel = hiltViewModel()
            val state by vehicleVm.state.collectAsStateWithLifecycle()
            VehicleSelectScreen(
                brands              = state.brands,
                categories          = state.categories,
                subcategories       = state.subcategories,
                models              = state.models,
                selectedBrand       = state.selectedBrand,
                selectedCategory    = state.selectedCategory,
                selectedSubcategory = state.selectedSubcategory,
                selectedModel       = state.selectedModel,
                onBrandSelect       = { vehicleVm.selectBrand(it) },
                onCategorySelect    = { vehicleVm.selectCategory(it) },
                onSubcategorySelect = { vehicleVm.selectSubcategory(it) },
                onModelSelect       = { vehicleVm.selectModel(it) },
                onConfirm           = {
                    state.selectedModel?.let { mainViewModel.selectVehicle(it) }
                    navController.popBackStack()
                },
                onBack              = { navController.popBackStack() }
            )
        }
        composable(
            Screen.Chat.route,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "both" },
                navArgument("q")    { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val mode         = backStackEntry.arguments?.getString("mode") ?: "both"
            val rawQuery     = backStackEntry.arguments?.getString("q")
            val initialQuery = remember(rawQuery) {
                rawQuery?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            }
            val chatVm: ChatViewModel = hiltViewModel()
            val state by chatVm.state.collectAsStateWithLifecycle()

            LaunchedEffect(selectedVehicleId, mode) {
                chatVm.clearForChat(selectedVehicleId, mode)
            }
            LaunchedEffect(initialQuery) {
                if (initialQuery != null && state.messages.isEmpty()) {
                    chatVm.sendMessage(initialQuery, mode, selectedVehicleId)
                }
            }

            ChatScreen(
                title                  = when (mode) { "diagnosis" -> "Диагностика"; "accessory" -> "Аксессуары"; else -> "Чат" },
                messages               = state.messages,
                riskLevel              = state.riskLevel,
                requiresEvacuation     = state.requiresEvacuation,
                isGenerating           = state.isGenerating,
                isModelReady           = state.isModelReady,
                selectedVehicleName    = selectedVehicleName,
                allOfflineModels       = state.allOfflineModels,
                activeOfflineModelId   = state.activeOfflineModelId,
                currentOnlineProvider  = state.currentOnlineProvider,
                selectedLlmModelId     = state.selectedLlmModelId,
                selectedOnlineProvider = state.selectedOnlineProvider,
                onSelectOfflineLlm     = { chatVm.selectOfflineLlm(it) },
                onSelectOnlineLlm      = { chatVm.selectOnlineLlm(it) },
                onResetLlm             = { chatVm.resetLlmSelection() },
                onSend                 = { msg -> chatVm.sendMessage(msg, mode, selectedVehicleId) },
                onNavigate             = { route -> navController.navigate(route) },
                onBack                 = { navController.popBackStack() }
            )
        }
        composable(Screen.Diagnose.route) {
            val chatVm: ChatViewModel = hiltViewModel()
            val state by chatVm.state.collectAsStateWithLifecycle()
            DiagnoseScreen(
                selectedVehicle    = selectedVehicle,
                messages           = state.messages,
                riskLevel          = state.riskLevel,
                requiresEvacuation = state.requiresEvacuation,
                isGenerating       = state.isGenerating,
                isModelReady       = state.isModelReady,
                commonSymptoms     = listOf(
                    "Двигатель не заводится", "Не включается 4WD",
                    "Проблемы с CVT ремнём", "Перегрев двигателя",
                    "iBR не работает (Sea-Doo)", "Глохнет на холостых (850 E-TEC)",
                    "Рвётся ремень (Ski-Doo/Lynx)", "Проблемы с DCT (Maverick R)"
                ),
                onSend           = { text -> navController.navigate(Screen.Chat.createRoute("diagnosis", text)) },
                onSelectVehicle  = { navController.navigate(Screen.VehicleSelect.route) },
                onGoToSituations = { navController.navigate(Screen.Situations.route) },
                onNavigate       = { route -> navController.navigate(route) },
                onBack           = { navController.popBackStack() }
            )
        }
        composable(Screen.AccessoryShop.route) {
            val chatVm: ChatViewModel = hiltViewModel()
            val state by chatVm.state.collectAsStateWithLifecycle()
            AccessoryShopScreen(
                selectedVehicle      = selectedVehicle,
                messages             = state.messages,
                isGenerating         = state.isGenerating,
                isModelReady         = state.isModelReady,
                popularCategories    = listOf(
                    "Хранение LinQ", "Защита", "Комфорт", "Освещение",
                    "Аудио", "Лебёдки", "Ветровые стёкла", "Крыши/Кабины"
                ),
                suggestedAccessories = emptyList(),
                onSend               = { text -> navController.navigate(Screen.Chat.createRoute("accessory", text)) },
                onCategorySelect     = { cat ->
                    navController.navigate(Screen.Chat.createRoute("accessory", "Хочу аксессуары из категории $cat"))
                },
                onSelectVehicle      = { navController.navigate(Screen.VehicleSelect.route) },
                onNavigate           = { route -> navController.navigate(route) },
                onBack               = { navController.popBackStack() }
            )
        }
        composable(Screen.ModelManager.route) {
            val modelVm: ModelManagerViewModel = hiltViewModel()
            val state by modelVm.state.collectAsStateWithLifecycle()
            ModelManagerScreen(
                state                = state,
                onDownload           = { modelVm.downloadModel(it) },
                onActivate           = { modelVm.activateModel(it) },
                onDelete             = { modelVm.deleteModel(it) },
                onAddFromFile        = { uri, name -> modelVm.addCustomModelFromFile(uri, name) },
                onAddFromUrl         = { title, url -> modelVm.addCustomModelFromUrl(title, url) },
                onUpdateApiKey       = { modelVm.updateApiKey(it) },
                onUpdateProvider     = { modelVm.updateAiProvider(it) },
                onUpdateModel        = { modelVm.updateAiModel(it) },
                onUpdateSystemPrompt = { modelVm.updateSystemPrompt(it) },
                onUpdateTemperature  = { modelVm.updateTemperature(it) },
                onClearError         = { modelVm.clearError() },
                onNavigate           = { route -> navController.navigate(route) },
                onBack               = { navController.popBackStack() }
            )
        }
        composable(Screen.Compare.route) {
            val compareVm: com.brp.assistant.ui.compare.CompareViewModel = hiltViewModel()
            val state by compareVm.state.collectAsStateWithLifecycle()
            CompareScreen(
                allModels        = state.allModels,
                selectedModels   = state.selectedModels,
                onToggleModel    = { compareVm.toggleModel(it) },
                onClearSelection = { compareVm.clearSelection() },
                onCompareWithAI  = {
                    val names = state.selectedModels.joinToString(" и ") { it.modelName }
                    navController.navigate(Screen.Chat.createRoute("both", "Сравни эти модели: $names"))
                },
                onBack           = { navController.popBackStack() }
            )
        }
    }
}
