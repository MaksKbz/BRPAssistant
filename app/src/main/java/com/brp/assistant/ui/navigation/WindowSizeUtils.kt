package com.brp.assistant.ui.navigation

import android.app.Activity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Возвращает [WindowSizeClass] для адаптивной навигации.
 * Вызывай в композабльных функциях внутри Activity-контекста.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val activity = LocalContext.current as Activity
    return calculateWindowSizeClass(activity)
}

/**
 * Труе, если экран широкий (планшет, раскладной в раскрытом виде).
 * Именно сдесь принимается решение о переходе на NavigationRail.
 */
val WindowSizeClass.isExpanded: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Expanded

/**
 * Труе, если экран средней ширины (большой телефон в альбомной ориентации).
 */
val WindowSizeClass.isMedium: Boolean
    get() = widthSizeClass == WindowWidthSizeClass.Medium
