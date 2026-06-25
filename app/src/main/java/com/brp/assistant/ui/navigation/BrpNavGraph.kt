package com.brp.assistant.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
    data object ModelManager : Screen("model-manager", "Настройки ИИ")
    data object Home : Screen("home", "Главная")
    data object Situations : Screen("situations", "Инструкции")
    data object Maintenance : Screen("maintenance", "Регламент")
    data object VehicleSelect : Screen("vehicle-select", "Моя техника")
    data object Chat : Screen("chat/{mode}?q={q}", "Чат") {
        fun createRoute(mode: String, query: String? = null): String {
            val encodedQuery = query?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.toString()) }
            return "chat/$mode" + (if (encodedQuery != null) "?q=$encodedQuery" else "")
        }
    }
    data object Diagnose : Screen("diagnose", "Диагностика")
    data object AccessoryShop : Screen("accessory", "Аксессуары")
    data object Compare : Screen("compare", "Сравнение")
}

private val bottomScreens = listOf(Screen.Home, Screen.Diagnose, Screen.AccessoryShop, Screen.VehicleSelect)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrpNavGraph(
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomScreens.map { it.route } ||
            currentRoute?.startsWith("chat/") == true

    val selectedVehicleId by mainViewModel.selectedVehicleId.collectAsStateWithLifecycle()
    val selectedVehicleName by mainViewModel.selectedVehicleName.collectAsStateWithLifecycle()
    val selectedVehicle by mainViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val activeModelName by mainViewModel.activeModelName.collectAsStateWithLifecycle()
    val currentTheme by mainViewModel.appTheme.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomScreens.forEach { screen ->
                        val selected = currentRoute?.startsWith(screen.route) == true
                        NavigationBarItem(
                            icon = {
                                when (screen) {
                                    Screen.Home -> Icon(Icons.Default.Home, null)
                                    Screen.Diagnose -> Icon(Icons.Default.Build, null)
                                    Screen.AccessoryShop -> Icon(Icons.Default.ShoppingBag, null)
                                    Screen.VehicleSelect -> Icon(Icons.Default.DirectionsCar, null)
                                    else -> Icon(Icons.Default.Help, null)
                                }
                            },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    selectedVehicleName = selectedVehicleName,
                    activeModelName = activeModelName,
                    currentTheme = currentTheme,
                    onSetTheme = { mainViewModel.setAppTheme(it) },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Screen.Situations.route) {
                val situVm: SituationsViewModel = hiltViewModel()
                val state by situVm.state.collectAsStateWithLifecycle()
                SituationsScreen(
                    categories = state.categories,
                    nodes = state.nodes,
                    cards = state.cards,
                    selectedCategory = state.selectedCategory,
                    selectedNode = state.selectedNode,
                    selectedVehicle = selectedVehicle,
                    onCategorySelect = { situVm.selectCategory(it) },
                    onNodeSelect = { node, isEl -> situVm.selectNode(node, isEl) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Maintenance.route) {
                val maintVm: MaintenanceViewModel = hiltViewModel()
                val state by maintVm.state.collectAsStateWithLifecycle()
                MaintenanceScreen(
                    selectedVehicle = selectedVehicle,
                    purchaseDate = state.purchaseDate,
                    onUpdatePurchaseDate = { maintVm.updatePurchaseDate(it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.VehicleSelect.route) {
                val vehicleVm: VehicleSelectViewModel = hiltViewModel()
                val state by vehicleVm.state.collectAsStateWithLifecycle()
                VehicleSelectScreen(
                    brands = state.brands,
                    categories = state.categories,
                    subcategories = state.subcategories,
                    models = state.models,
                    selectedBrand = state.selectedBrand,
                    selectedCategory = state.selectedCategory,
                    selectedSubcategory = state.selectedSubcategory,
                    selectedModel = state.selectedModel,
                    onBrandSelect = { vehicleVm.selectBrand(it) },
                    onCategorySelect = { vehicleVm.selectCategory(it) },
                    onSubcategorySelect = { vehicleVm.selectSubcategory(it) },
                    onModelSelect = { vehicleVm.selectModel(it) },
                    onConfirm = {
                        state.selectedModel?.let { mainViewModel.selectVehicle(it) }
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                Screen.Chat.route,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType; defaultValue = "both" },
                    navArgument("q") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val mode = backStackEntry.arguments?.getString("mode") ?: "both"
                val rawQuery = backStackEntry.arguments?.getString("q")
                val initialQuery = remember(rawQuery) {
                    rawQuery?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                }
                val chatVm: ChatViewModel = hiltViewModel()
                val state by chatVm.state.collectAsStateWithLifecycle()

                // Очищаем контекст чата при смене техники или режима
                LaunchedEffect(selectedVehicleId, mode) {
                    chatVm.clearForChat(selectedVehicleId, mode)
                }

                LaunchedEffect(initialQuery) {
                    if (initialQuery != null && state.messages.isEmpty()) {
                        chatVm.sendMessage(initialQuery, mode, selectedVehicleId)
                    }
                }

                ChatScreen(
                    title = when (mode) {
                        "diagnosis" -> "Диагностика"
                        "accessory" -> "Подбор аксессуаров"
                        else -> "Чат"
                    },
                    messages = state.messages,
                    riskLevel = state.riskLevel,
                    requiresEvacuation = state.requiresEvacuation,
                    isGenerating = state.isGenerating,
                    isModelReady = state.isModelReady,
                    selectedVehicleName = selectedVehicleName,
                    availableOfflineModels = state.availableOfflineModels,
                    activeOfflineModelId = state.activeOfflineModelId,
                    currentOnlineProvider = state.currentOnlineProvider,
                    selectedLlmModelId = state.selectedLlmModelId,
                    selectedOnlineProvider = state.selectedOnlineProvider,
                    onSelectOfflineLlm = { chatVm.selectOfflineLlm(it) },
                    onSelectOnlineLlm = { chatVm.selectOnlineLlm(it) },
                    onResetLlm = { chatVm.resetLlmSelection() },
                    onSend = { msg -> chatVm.sendMessage(msg, mode, selectedVehicleId) },
                    onNavigate = { route -> navController.navigate(route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Diagnose.route) {
                val chatVm: ChatViewModel = hiltViewModel()
                val state by chatVm.state.collectAsStateWithLifecycle()
                DiagnoseScreen(
                    selectedVehicle = selectedVehicle,
                    messages = state.messages,
                    riskLevel = state.riskLevel,
                    requiresEvacuation = state.requiresEvacuation,
                    isGenerating = state.isGenerating,
                    isModelReady = state.isModelReady,
                    commonSymptoms = listOf(
                        "Двигатель не заводится",
                        "Не включается 4WD",
                        "Проблемы с CVT ремнём",
                        "Перегрев двигателя",
                        "iBR не работает (Sea-Doo)",
                        "Глохнет на холостых (850 E-TEC)",
                        "Рвётся ремень (Ski-Doo/Lynx)",
                        "Проблемы с DCT (Maverick R)"
                    ),
                    onSend = { text ->
                        navController.navigate(Screen.Chat.createRoute("diagnosis", text))
                    },
                    onSelectVehicle = { navController.navigate(Screen.VehicleSelect.route) },
                    onGoToSituations = { navController.navigate(Screen.Situations.route) },
                    onNavigate = { route -> navController.navigate(route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AccessoryShop.route) {
                val chatVm: ChatViewModel = hiltViewModel()
                val state by chatVm.state.collectAsStateWithLifecycle()
                AccessoryShopScreen(
                    selectedVehicle = selectedVehicle,
                    messages = state.messages,
                    isGenerating = state.isGenerating,
                    isModelReady = state.isModelReady,
                    popularCategories = listOf(
                        "Хранение LinQ", "Защита", "Комфорт", "Освещение",
                        "Аудио", "Лебёдки", "Ветровые стёкла", "Крыши/Кабины"
                    ),
                    suggestedAccessories = emptyList(),
                    onSend = { text ->
                        navController.navigate(Screen.Chat.createRoute("accessory", text))
                    },
                    onCategorySelect = { cat ->
                        navController.navigate(
                            Screen.Chat.createRoute("accessory", "Хочу аксессуары из категории $cat")
                        )
                    },
                    onSelectVehicle = { navController.navigate(Screen.VehicleSelect.route) },
                    onNavigate = { route -> navController.navigate(route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ModelManager.route) {
                val modelVm: ModelManagerViewModel = hiltViewModel()
                val state by modelVm.state.collectAsStateWithLifecycle()
                ModelManagerScreen(
                    state = state,
                    onDownload = { modelVm.downloadModel(it) },
                    onActivate = { modelVm.activateModel(it) },
                    onDelete = { modelVm.deleteModel(it) },
                    onAddFromFile = { uri, name -> modelVm.addCustomModelFromFile(uri, name) },
                    onAddFromUrl = { title, url -> modelVm.addCustomModelFromUrl(title, url) },
                    onUpdateApiKey = { modelVm.updateApiKey(it) },
                    onUpdateProvider = { modelVm.updateAiProvider(it) },
                    onUpdateModel = { modelVm.updateAiModel(it) },
                    onUpdateSystemPrompt = { modelVm.updateSystemPrompt(it) },
                    onUpdateTemperature = { modelVm.updateTemperature(it) },
                    onClearError = { modelVm.clearError() },
                    onNavigate = { route -> navController.navigate(route) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Compare.route) {
                val compareVm: com.brp.assistant.ui.compare.CompareViewModel = hiltViewModel()
                val state by compareVm.state.collectAsStateWithLifecycle()
                CompareScreen(
                    allModels = state.allModels,
                    selectedModels = state.selectedModels,
                    onToggleModel = { compareVm.toggleModel(it) },
                    onClearSelection = { compareVm.clearSelection() },
                    onCompareWithAI = {
                        val names = state.selectedModels.joinToString(" и ") { it.modelName }
                        navController.navigate(Screen.Chat.createRoute("both", "Сравни эти модели: $names"))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
