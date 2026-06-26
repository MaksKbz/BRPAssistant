package com.brp.assistant.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brp.assistant.ui.navigation.BrpNavGraph
import com.brp.assistant.ui.theme.BRPAssistantTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val themeMode by mainViewModel.appTheme.collectAsStateWithLifecycle()

            // Вычисляем WindowSizeClass — реагирует на поворот экрана и состояние фолдабла
            val windowSizeClass = calculateWindowSizeClass(this)

            val isDark = when (themeMode) {
                "Dark"  -> true
                "Light" -> false
                else    -> isSystemInDarkTheme()
            }

            BRPAssistantTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    BrpNavGraph(
                        mainViewModel  = mainViewModel,
                        widthSizeClass = windowSizeClass.widthSizeClass
                    )
                }
            }
        }
    }
}
