package com.brp.assistant.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Адаптивная обёртка навигации.
 *
 * Поведение:
 *  - Телефон (Compact)     → [NavigationBar]  (панель снизу)
 *  - Планшет / Expanded  → [NavigationRail] (панель слева)
 *
 * @param windowSizeClass  актуальный размер окна
 * @param currentRoute     текущий route навигации
 * @param onNavigate       коллбек перехода на route
 * @param content          основной контент
 */
@Composable
fun AdaptiveNavigationLayout(
    windowSizeClass: WindowSizeClass,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    if (windowSizeClass.isExpanded) {
        // ── Планшет: NavigationRail слева ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                header = {
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "BRP",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "BRP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }
            ) {
                bottomScreens.forEach { screen ->
                    val selected = currentRoute?.startsWith(screen.route) == true
                    NavigationRailItem(
                        icon = { NavIcon(screen) },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = { onNavigate(screen.route) }
                    )
                }
            }

            // Основной контент занимает всё оставшееся пространство
            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues(0.dp))
            }
        }
    } else {
        // ── Телефон: Scaffold с BottomNavigationBar ────────────────────
        Scaffold(
            bottomBar = {
                if (currentRoute != null && isBottomBarRoute(currentRoute)) {
                    NavigationBar {
                        bottomScreens.forEach { screen ->
                            val selected = currentRoute.startsWith(screen.route)
                            NavigationBarItem(
                                icon = { NavIcon(screen) },
                                label = { Text(screen.label) },
                                selected = selected,
                                onClick = { onNavigate(screen.route) }
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            content(innerPadding)
        }
    }
}

/**
 * Определяет, надо ли показывать BottomBar для данного route.
 */
fun isBottomBarRoute(route: String): Boolean {
    return bottomScreens.any { route.startsWith(it.route) } || route.startsWith("chat/")
}

@Composable
private fun NavIcon(screen: Screen) {
    when (screen) {
        Screen.Home          -> Icon(Icons.Default.Home,         contentDescription = null)
        Screen.Diagnose      -> Icon(Icons.Default.Build,        contentDescription = null)
        Screen.AccessoryShop -> Icon(Icons.Default.ShoppingBag, contentDescription = null)
        Screen.VehicleSelect -> Icon(Icons.Default.DirectionsCar, contentDescription = null)
        else                 -> Icon(Icons.Default.Help,         contentDescription = null)
    }
}
