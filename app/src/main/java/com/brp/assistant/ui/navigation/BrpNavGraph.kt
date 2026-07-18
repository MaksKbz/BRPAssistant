package com.brp.assistant.ui.navigation

import android.content.res.Configuration
import com.brp.assistant.data.db.entities.BrpModel
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
import com.brp.assistant.ui.compare.CompareViewModel
import com.brp.assistant.ui.diagnose.DiagnoseScreen
import com.brp.assistant.ui.docs.UserDocsScreen
import com.brp.assistant.ui.home.HomeScreen
import com.brp.assistant.ui.model.ModelManagerScreen
import com.brp.assistant.ui.model.ModelManagerViewModel
import com.brp.assistant.ui.onboarding.OnboardingScreen
import com.brp.assistant.ui.situations.SituationsScreen
import com.brp.assistant.ui.situations.SituationsViewModel
import com.brp.assistant.ui.maintenance.MaintenanceScreen
import com.brp.assistant.ui.maintenance.MaintenanceViewModel
import com.brp.assistant.ui.vehicle.VehicleSelectScreen
import com.brp.assistant.ui.vehicle.VehicleSelectViewModel
import java.lang.ref.WeakReference

sealed class Screen(val route: String, val label: String) {
    data object ModelManager  : Screen("model-manager",  "Настройки ИИ")
    data object UserDocs      : Screen("user-docs",      "Моя база")
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
    data object Onboarding    : Screen("onboarding", "Приветствие")
}

private val bottomScreens = listOf(
    Screen.Home, Screen.Diagnose, Screen.AccessoryShop, Screen.VehicleSelect
)

private fun navigateSafe(
    navController: androidx.navigation.NavHostController,
    route: String
) {
    if (route == Screen.Home.route) {
        navController.popBackStack(Screen.Home.route, inclusive = false)
    } else {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState    = true
        }
    }
}

private data class NavItem(
    val screen: Screen,
    val iconContent: @Composable () -> Unit
)

private val navItems = listOf(
    NavItem(screen = Screen.Home)          { Icon(Icons.Default.Home,          contentDescription = Screen.Home.label) },
    NavItem(screen = Screen.Diagnose)      { Icon(Icons.Default.Build,         contentDescription = Screen.Diagnose.label) },
    NavItem(screen = Screen.AccessoryShop) { Icon(Icons.Default.ShoppingBag,   contentDescription = Screen.AccessoryShop.label) },
    NavItem(screen = Screen.VehicleSelect) { Icon(Icons.Default.DirectionsCar, contentDescription = Screen.VehicleSelect.label) }
)

object SharedChatVmHolder {
    private var ref: WeakReference<ChatViewModel>? = null
    var chatVm: ChatViewModel?
        get()      = ref?.get()
        set(value) { ref = value?.let { WeakReference(it) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrpNavGraph(
    navController: NavHostController     = rememberNavController(),
    mainViewModel: MainViewModel         = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route

    // null = ещё читаем из DataStore — не рендерим ничего до готовности
    val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    val showNav = currentRoute in bottomScreens.map { it.route } ||
            currentRoute?.startsWith("chat/") == true ||
            currentRoute == Screen.Situations.route ||
            currentRoute == Screen.Maintenance.route ||
            currentRoute == Screen.Compare.route

    val selectedVehicleId   by mainViewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val selectedVehicleName by mainViewModel.selectedVehicleName.collectAsStateWithLifecycle()
    val selectedVehicle: BrpModel? by mainViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val activeModelName     by mainViewModel.activeModelName.collectAsStateWithLifecycle()
    val currentTheme        by mainViewModel.appTheme.collectAsStateWithLifecycle()
    val healthWarning       by mainViewModel.healthWarning.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val effectiveSizeClass = when {
        widthSizeClass == WindowWidthSizeClass.Expanded -> WindowWidthSizeClass.Expanded
        widthSizeClass == WindowWidthSizeClass.Medium   -> WindowWidthSizeClass.Medium
        configuration.screenWidthDp >= 480              -> WindowWidthSizeClass.Medium
        else                                             -> WindowWidthSizeClass.Compact
    }

    // Пока читаем флаг из DataStore — показываем splash
    if (onboardingCompleted == null) {
        Box(
            modifier            = Modifier.fillMaxSize(),
            contentAlignment    = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Стартовый экран: onboarding для новых, home для остальных
    val startDest = if (onboardingCompleted == true) Screen.Home.route else Screen.Onboarding.route

    when {
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
                    healthWarning       = healthWarning,
                    widthSizeClass      = effectiveSizeClass,
                    startDestination    = startDest,
                    modifier            = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.ime)
                )
            }
        }
        else -> {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
                bottomBar = {
                    if (showNav) {
                        NavigationBar {
                            navItems.forEach { item ->
                                val selected = currentRoute?.startsWith(item.screen.route) == true
                                NavigationBarItem(
                                    icon     = item.iconContent,
                                    label    = { Text(item.screen.label) },
                                    selected = selected,
                                    onClick  = { navigateSafe(navController, item.screen.route) }
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
                    healthWarning       = healthWarning,
                    widthSizeClass      = effectiveSizeClass,
                    startDestination    = startDest,
                    modifier            = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedLayout(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    currentRoute: String?,
    selectedVehicleId: String?,
    selectedVehicleName: String?,
    selectedVehicle: BrpModel?,
    activeModelName: String?,
    currentTheme: String,
    widthSizeClass: WindowWidthSizeClass
) {
    val isChatRoute = currentRoute?.startsWith("chat/") == true
    val vm = SharedChatVmHolder.chatVm
    val chatState = vm?.state?.collectAsStateWithLifecycle()
    val healthWarning by mainViewModel.healthWarning.collectAsStateWithLifecycle()
    val onboardingCompleted by mainViewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val startDest = if (onboardingCompleted == true) Screen.Home.route else Screen.Onboarding.route

    var panelSearch by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        PermanentDrawerSheet(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
        ) {
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

            navItems.forEach { item ->
                val selected = currentRoute?.startsWith(item.screen.route) == true
                NavigationDrawerItem(
                    icon     = item.iconContent,
                    label    = { Text(item.screen.label) },
                    selected = selected,
                    onClick  = { navigateSafe(navController, item.screen.route) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!selectedVehicleName.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(text = selectedVehicleName, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!activeModelName.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null,
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(text = activeModelName, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        if (isChatRoute && chatState != null) {
            Surface(
                modifier = Modifier.width(280.dp).fillMaxHeight(),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    OutlinedButton(
                        onClick  = { vm?.startNewChat(selectedVehicleId, selectedVehicleName, "both") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Новый чат")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = panelSearch, onValueChange = { panelSearch = it },
                        placeholder   = { Text("Поиск", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon  = if (panelSearch.isNotEmpty()) {{
                            IconButton(onClick = { panelSearch = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить", modifier = Modifier.size(16.dp))
                            }
                        }} else null,
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle  = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("История", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    Spacer(Modifier.height(4.dp))
                    val sessions = chatState.value.sessionHistory
                    val filteredPanel = remember(sessions, panelSearch) {
                        val q = panelSearch.trim().lowercase()
                        if (q.isEmpty()) sessions
                        else sessions.filter {
                            it.title.lowercase().contains(q) ||
                                    it.vehicleName?.lowercase()?.contains(q) == true
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(filteredPanel, key = { it.id }) { session ->
                            val isSelected = session.id == chatState.value.selectedSessionId
                            Surface(
                                onClick = { vm?.loadSession(session.id) },
                                shape   = MaterialTheme.shapes.small,
                                color   = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                          else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(text = session.title, style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (!session.vehicleName.isNullOrBlank()) {
                                        Text(text = session.vehicleName, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(text = session.dateLabel, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        NavHostContent(
            navController       = navController,
            mainViewModel       = mainViewModel,
            selectedVehicleId   = selectedVehicleId,
            selectedVehicleName = selectedVehicleName,
            selectedVehicle     = selectedVehicle,
            activeModelName     = activeModelName,
            currentTheme        = currentTheme,
            healthWarning       = healthWarning,
            startDestination    = startDest,
            widthSizeClass      = WindowWidthSizeClass.Medium,
            modifier            = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun BrpNavigationRail(
    navController: NavHostController,
    currentRoute: String?
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        header = {
            Icon(imageVector = Icons.Default.TwoWheeler, contentDescription = "BRP",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).size(28.dp))
        }
    ) {
        Spacer(Modifier.weight(1f))
        navItems.forEach { item ->
            val selected = currentRoute?.startsWith(item.screen.route) == true
            NavigationRailItem(
                icon     = item.iconContent,
                label    = { Text(item.screen.label) },
                selected = selected,
                onClick  = { navigateSafe(navController, item.screen.route) }
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
    selectedVehicle: BrpModel?,
    activeModelName: String?,
    currentTheme: String,
    healthWarning: String?,
    startDestination: String = Screen.Home.route,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier
    ) {
        // Онбординг: показывается только если onboardingCompleted == false
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                totalRamGb = mainViewModel.totalRamGb,
                deviceInfo = mainViewModel.deviceInfo,
                onFinish   = {
                    mainViewModel.completeOnboarding()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                healthWarning           = healthWarning,
                onNavigateToChat        = { mode -> navController.navigate(Screen.Chat.createRoute(mode)) },
                onNavigateToDiagnose    = { navigateSafe(navController, Screen.Diagnose.route) },
                onNavigateToCompare     = { navigateSafe(navController, Screen.Compare.route) },
                onNavigateToMaintenance = { navigateSafe(navController, Screen.Maintenance.route) },
                onNavigateToAccessory   = { navigateSafe(navController, Screen.AccessoryShop.route) },
                onNavigateToSituations  = { navigateSafe(navController, Screen.Situations.route) },
                onNavigateToVehicle     = { navigateSafe(navController, Screen.VehicleSelect.route) },
                onNavigateToModel       = { navigateSafe(navController, Screen.ModelManager.route) },
                onNavigateToUserDocs    = { navigateSafe(navController, Screen.UserDocs.route) },
                onNavigateToSettings    = { navigateSafe(navController, Screen.ModelManager.route) }
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
        composable(Screen.Compare.route) {
            val compareVm: CompareViewModel = hiltViewModel()
            val state by compareVm.state.collectAsStateWithLifecycle()
            CompareScreen(
                allModels           = state.allModels,
                selectedModels      = state.selectedModels,
                showLimitToast      = state.showLimitToast,
                onToggleModel       = { compareVm.toggleModel(it) },
                onClearSelection    = { compareVm.clearSelection() },
                onCompareWithAI     = {
                    val names = state.selectedModels.joinToString(", ") { it.modelName }
                    navController.navigate(Screen.Chat.createRoute("both", "Сравни технику BRP: $names"))
                },
                onDismissLimitToast = { compareVm.dismissLimitToast() },
                onBack              = { navController.popBackStack() }
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
                hasOnlineKeyMissing    = state.hasOnlineKeyMissing,
                healthWarning          = healthWarning,
                searchQuery            = state.searchQuery,
                onSearchQueryChange    = { chatVm.updateSearchQuery(it) },
                onSelectOfflineLlm     = { chatVm.selectOfflineLlm(it) },
                onSelectOnlineLlm      = { chatVm.selectOnlineLlm(it) },
                onResetLlm             = { chatVm.resetLlmSelection() },
                onSend                 = { msg -> chatVm.sendMessage(msg, mode, selectedVehicleId) },
                onNavigate             = { route -> navigateSafe(navController, route) },
                onBack                 = { navController.popBackStack() },
                widthSizeClass         = widthSizeClass,
                sessionHistory         = state.sessionHistory,
                selectedSessionId      = state.selectedSessionId,
                onSelectSession        = { id -> chatVm.loadSession(id) },
                onNewChat              = { chatVm.startNewChat(selectedVehicleId, selectedVehicleName, mode) },
                onDeleteSession        = { id -> chatVm.deleteSession(id) }
            )
        }
        composable(Screen.Diagnose.route) {
            DiagnoseScreen(
                selectedVehicle    = selectedVehicle,
                messages           = emptyList(),
                riskLevel          = "low",
                requiresEvacuation = false,
                isGenerating       = false,
                isModelReady       = true,
                commonSymptoms     = listOf(
                    "Двигатель не заводится", "Не включается 4WD",
                    "Проблемы с CVT ремнём", "Перегрев двигателя",
                    "iBR не работает (Sea-Doo)", "Глохнет на холостых (850 E-TEC)",
                    "Рвётся ремень (Ski-Doo/Lynx)", "Проблемы с DCT (Maverick R)"
                ),
                onSend           = { text -> navController.navigate(Screen.Chat.createRoute("diagnosis", text)) },
                onSelectVehicle  = { navigateSafe(navController, Screen.VehicleSelect.route) },
                onGoToSituations = { navigateSafe(navController, Screen.Situations.route) },
                onNavigate       = { route -> navigateSafe(navController, route) },
                onBack           = { navController.popBackStack() }
            )
        }
        composable(Screen.AccessoryShop.route) {
            AccessoryShopScreen(
                selectedVehicle      = selectedVehicle,
                messages             = emptyList(),
                isGenerating         = false,
                isModelReady         = true,
                popularCategories    = listOf(
                    "Хранение LinQ", "Защита", "Комфорт", "Освещение",
                    "Аудио", "Лебёдки", "Ветровые стёкла", "Крыши/Кабины"
                ),
                suggestedAccessories = emptyList(),
                onSend               = { text -> navController.navigate(Screen.Chat.createRoute("accessory", text)) },
                onCategorySelect     = { cat ->
                    navController.navigate(Screen.Chat.createRoute("accessory", "Хочу аксессуары из категории $cat"))
                },
                onSelectVehicle      = { navigateSafe(navController, Screen.VehicleSelect.route) },
                onNavigate           = { route -> navigateSafe(navController, route) },
                onBack               = { navController.popBackStack() }
            )
        }
        composable(Screen.ModelManager.route) {
            val modelVm: ModelManagerViewModel = hiltViewModel()
            val state by modelVm.state.collectAsStateWithLifecycle()
            ModelManagerScreen(
                state                      = state,
                onDownload                 = { modelVm.downloadModel(it) },
                onActivate                 = { modelVm.activateModel(it) },
                onDelete                   = { modelVm.deleteModel(it) },
                onAddFromFile              = { uri, name -> modelVm.addCustomModelFromFile(uri, name) },
                onAddFromUrl               = { title, url -> modelVm.addCustomModelFromUrl(title, url) },
                onUpdateApiKey             = { modelVm.updateApiKey(it) },
                onUpdateProvider           = { modelVm.updateAiProvider(it) },
                onUpdateModel              = { modelVm.updateAiModel(it) },
                onUpdateSystemPrompt       = { modelVm.updateSystemPrompt(it) },
                onUpdateTemperature        = { modelVm.updateTemperature(it) },
                onConfirmUnsafeDownload    = { modelVm.confirmUnsafeDownload() },
                onDismissPendingWarning    = { modelVm.dismissPendingWarning() },
                onClearError               = { modelVm.clearError() },
                onNavigate                 = { route -> navigateSafe(navController, route) },
                onBack                     = { navController.popBackStack() }
            )
        }
        composable(Screen.UserDocs.route) {
            UserDocsScreen(onBack = { navController.popBackStack() })
        }
    }
}
