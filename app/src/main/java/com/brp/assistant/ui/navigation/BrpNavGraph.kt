package com.brp.assistant.ui.navigation

import android.content.res.Configuration
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route

    val showNav = currentRoute in bottomScreens.map { it.route } ||
            currentRoute?.startsWith("chat/") == true

    val selectedVehicleId   by mainViewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val selectedVehicleName by mainViewModel.selectedVehicleName.collectAsStateWithLifecycle()
    val selectedVehicle     by mainViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val activeModelName     by mainViewModel.activeModelName.collectAsStateWithLifecycle()
    val currentTheme        by mainViewModel.appTheme.collectAsStateWithLifecycle()

    // FIX #6: 7" планшеты в portrait дают Compact (360-599dp), в landscape — Medium.
    // Переворот менял паттерн навигации с BottomBar на Rail, вызывая дезориентацию.
    // Если ширина >= 480dp, принудительно используем Medium независимо от WindowWidthSizeClass.
    val configuration = LocalConfiguration.current
    val effectiveSizeClass = when {
        widthSizeClass == WindowWidthSizeClass.Expanded -> WindowWidthSizeClass.Expanded
        widthSizeClass == WindowWidthSizeClass.Medium   -> WindowWidthSizeClass.Medium
        configuration.screenWidthDp >= 480              -> WindowWidthSizeClass.Medium
        else                                             -> WindowWidthSizeClass.Compact
    }

    when {
        // ── EXPANDED (планшеты ≥840dp): постоянный NavigationDrawer + панель сессий ───
        effectiveSizeClass == WindowWidthSizeClass.Expanded && showNav -> {
            ExpandedLayout(
                navController       = navController,
                mainViewModel       = mainViewModel,
                currentRoute        = currentRoute,
                selectedVehicleId   = selectedVehicleId,
                selectedVehicleName = selectedVehicleName,
                selectedVehicle     = selectedVehicle,
                activeModelName     = activeModelName,
                currentTheme        = currentTheme,
                widthSizeClass      = effectiveSizeClass
            )
        }
        // ── MEDIUM (планшеты/fold ≥600dp): NavigationRail ─────────────────────────────
        effectiveSizeClass == WindowWidthSizeClass.Medium && showNav -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                BrpNavigationRail(
                    navController = navController,
                    currentRoute  = currentRoute
                )
                NavHostContent(
                    navController       = navController,
                    mainViewModel       = mainViewModel,
                    selectedVehicleId   = selectedVehicleId,
                    selectedVehicleName = selectedVehicleName,
                    selectedVehicle     = selectedVehicle,
                    activeModelName     = activeModelName,
                    currentTheme        = currentTheme,
                    widthSizeClass      = effectiveSizeClass,
                    modifier            = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        // ── COMPACT (телефон): BottomNavigationBar ──────────────────────────────────
        else -> {
            Scaffold(
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
                    widthSizeClass      = effectiveSizeClass,
                    modifier            = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

/**
 * FIX #7: постоянный NavigationDrawer + панель сессий для Expanded.
 * FIX #2: chatVm передаётся извне (из NavHostContent через параметр),
 *         чтобы гарантировать единственный экземпляр ChatViewModel
 *         для панели сессий в drawer и основного чата.
 *
 * Макет при widthSizeClass.Expanded:
 *
 *  ┌────────────────┬──────────────────┬────────────────────────────────┐
 *  │ NavDrawer      │ Панель сессий    │     Основной контент           │
 *  │  240dp         │  280dp           │     (weight 1f)                │
 *  │                │  (только chat/)  │                                │
 *  └────────────────┴──────────────────┴────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedLayout(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    currentRoute: String?,
    selectedVehicleId: String?,
    selectedVehicleName: String?,
    selectedVehicle: Any?,
    activeModelName: String?,
    currentTheme: String,
    widthSizeClass: WindowWidthSizeClass
) {
    // FIX #2: chatVm НЕ создаётся здесь через hiltViewModel().
    // Вместо этого он создаётся в NavHostContent (composable chat/-маршрута)
    // и передаётся сюда через SharedChatVmHolder — единый ViewModelStoreOwner.
    // Это предотвращает рассинхронизацию состояния между панелью сессий
    // в ExpandedLayout и основным чатом в NavHostContent.
    val isChatRoute = currentRoute?.startsWith("chat/") == true
    val chatState = SharedChatVmHolder.chatVm?.state?.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // ── Постоянный дравер 240dp ───────────────────────────────────────
        PermanentDrawerSheet(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
        ) {
            // Шапка дравера
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = Icons.Default.TwoWheeler,
                    contentDescription = "BRP",
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "BRP Assistant",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            // Навигационные пункты
            navItems.forEach { item ->
                val selected = currentRoute?.startsWith(item.screen.route) == true
                NavigationDrawerItem(
                    icon     = item.iconContent,
                    label    = { Text(item.screen.label) },
                    selected = selected,
                    onClick  = {
                        navController.navigate(item.screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            // Footer: техника + модель
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!selectedVehicleName.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text     = selectedVehicleName,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!activeModelName.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text     = activeModelName,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── Панель сессий 280dp — только в chat/-маршруте ───────────────
        if (isChatRoute && chatState != null) {
            val vm = SharedChatVmHolder.chatVm
            Surface(
                modifier      = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // Кнопка "Новый чат"
                    OutlinedButton(
                        onClick  = { vm?.startNewChat(selectedVehicleId, selectedVehicleName, "both") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Новый чат")
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "История",
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(Modifier.height(4.dp))

                    LazyColumn(
                        modifier            = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(chatState.value.sessionHistory, key = { it.id }) { session ->
                            val isSelected = session.id == chatState.value.selectedSessionId
                            Surface(
                                onClick        = { vm?.loadSession(session.id) },
                                shape          = MaterialTheme.shapes.small,
                                color          = if (isSelected)
                                    MaterialTheme.colorScheme.secondaryContainer
                                else
                                    MaterialTheme.colorScheme.surface,
                                modifier       = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text     = session.title,
                                        style    = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!session.vehicleName.isNullOrBlank()) {
                                        Text(
                                            text  = session.vehicleName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text  = session.dateLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Основной контент ─────────────────────────────────────────────────
        NavHostContent(
            navController       = navController,
            mainViewModel       = mainViewModel,
            selectedVehicleId   = selectedVehicleId,
            selectedVehicleName = selectedVehicleName,
            selectedVehicle     = selectedVehicle,
            activeModelName     = activeModelName,
            currentTheme        = currentTheme,
            // FIX #1: при Expanded передаём Medium в NavHostContent → ChatScreen,
            // чтобы внутренняя ChatHistoryPanel (isTablet = Medium) не рендерилась
            // повторно — панель уже есть в ExpandedLayout выше (560dp дублирования устранено).
            widthSizeClass      = WindowWidthSizeClass.Medium,
            modifier            = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

/**
 * FIX #2: синглтон-холдер для передачи единственного экземпляра ChatViewModel
 * между ExpandedLayout и NavHostContent без activityViewModel().
 *
 * ChatViewModel создаётся в composable(Screen.Chat.route) через hiltViewModel()
 * (правильный ViewModelStoreOwner — NavBackStackEntry) и сохраняется здесь.
 * ExpandedLayout читает его через SharedChatVmHolder.chatVm.
 *
 * Очищается при выходе из chat/-маршрута (DisposableEffect в NavHostContent).
 */
object SharedChatVmHolder {
    var chatVm: ChatViewModel? = null
}

@Composable
private fun BrpNavigationRail(
    navController: NavHostController,
    currentRoute: String?
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        header = {
            Icon(
                imageVector        = Icons.Default.TwoWheeler,
                contentDescription = "BRP",
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier
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
}

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

            // FIX #2: регистрируем единственный экземпляр ChatViewModel в холдере,
            // чтобы ExpandedLayout мог его читать без создания второго экземпляра.
            // DisposableEffect очищает ссылку при выходе из chat/-маршрута.
            DisposableEffect(chatVm) {
                SharedChatVmHolder.chatVm = chatVm
                onDispose { SharedChatVmHolder.chatVm = null }
            }

            LaunchedEffect(selectedVehicleId, mode) {
                chatVm.clearForChat(selectedVehicleId, selectedVehicleName, mode)
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
                onBack                 = { navController.popBackStack() },
                widthSizeClass         = widthSizeClass,
                sessionHistory         = state.sessionHistory,
                selectedSessionId      = state.selectedSessionId,
                onSelectSession        = { id -> chatVm.loadSession(id) },
                onNewChat              = { chatVm.startNewChat(selectedVehicleId, selectedVehicleName, mode) }
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
