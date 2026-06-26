package com.brp.assistant.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brp.assistant.ui.components.ActionCard
import com.brp.assistant.ui.components.SectionHeader
import com.brp.assistant.ui.navigation.Screen

/** Горизонтальная полоса быстрых симптомов BRP */
@Composable
private fun QuickDiagnoseStrip(
    symptoms: List<String>,
    onSymptomClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        symptoms.forEach { symptom ->
            SuggestionChip(
                onClick = { onSymptomClick(symptom) },
                label = { Text(symptom, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedVehicleName: String? = null,
    activeModelName: String? = null,
    currentTheme: String = "System",
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onSetTheme: (String) -> Unit = {},
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    var showContactDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Используем WindowWidthSizeClass вместо LocalConfiguration.screenWidthDp.
    // LocalConfiguration ненадёжен на фолдаблах и при freeform-окнах.
    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact

    val symptoms = listOf(
        "Двигатель не заводится",
        "Не включается 4WD",
        "Проблемы с CVT",
        "Перегрев",
        "iBR не работает",
        "Глохнет 850 E-TEC",
        "Рвётся ремень",
        "Проблемы DCT"
    )

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Выберите тему") },
            text = {
                Column {
                    listOf("System", "Light", "Dark").forEach { theme ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = currentTheme == theme,
                                onClick = { onSetTheme(theme); showThemeDialog = false }
                            )
                            Text(theme, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Закрыть") } }
        )
    }

    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            title = { Text("Сервисный центр BRP-Алматы") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Техническая поддержка и сервис")
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/77014670955"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                    ) { Text("WhatsApp", color = Color.White) }
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+77022149470"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Позвонить (+7 702 214 9470)") }
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://brp-almaty.kz/"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Перейти на сайт") }
                }
            },
            confirmButton = { TextButton(onClick = { showContactDialog = false }) { Text("Закрыть") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTablet) 32.dp else 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Header ─────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "BRP Assistant",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                )
                Text(
                    "Оффлайн-эксперт BRP 2026 v2.3.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = { showThemeDialog = true }) {
                Icon(
                    imageVector = if (currentTheme == "Dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "Theme",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Hero Card ────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                Column {
                    Text(
                        "Инструкции и решения",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Оффлайн база знаний для вашей техники",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onNavigate(Screen.Situations.route) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Открыть каталог") }
                }
            }
        }

        // ── Quick Diagnose Strip ─────────────────────────────────────────────
        Spacer(modifier = Modifier.height(12.dp))
        QuickDiagnoseStrip(
            symptoms = symptoms,
            onSymptomClick = { symptom ->
                onNavigate(Screen.Chat.createRoute("diagnosis", symptom))
            }
        )

        if (selectedVehicleName != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🚗", fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            selectedVehicleName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = { onNavigate(Screen.VehicleSelect.route) }) {
                        Text("Сменить")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Сервис и Регламент")

        // ── Адаптивный layout карточек ─────────────────────────────────────────
        if (isTablet) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionCard(
                    icon = Icons.Default.Event,
                    title = "Регламент ТО",
                    subtitle = "График работ",
                    onClick = { onNavigate(Screen.Maintenance.route) },
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = Icons.Default.SupportAgent,
                    title = "Сервис BRP",
                    subtitle = "BRP Almaty",
                    accentColor = Color(0xFF4CAF50),
                    onClick = { showContactDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(
                    icon = Icons.Default.Event,
                    title = "Регламент ТО",
                    subtitle = "График работ",
                    onClick = { onNavigate(Screen.Maintenance.route) }
                )
                ActionCard(
                    icon = Icons.Default.SupportAgent,
                    title = "Сервис BRP",
                    subtitle = "BRP Almaty",
                    accentColor = Color(0xFF4CAF50),
                    onClick = { showContactDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        SectionHeader("Техника и Сравнение")

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ActionCard(
                icon = Icons.Default.DirectionsCar,
                title = "Моя техника",
                subtitle = "Выбрать модель",
                accentColor = Color(0xFFFFC107),
                onClick = { onNavigate(Screen.VehicleSelect.route) },
                modifier = Modifier.weight(1f)
            )
            ActionCard(
                icon = Icons.Default.Compare,
                title = "Сравнение",
                subtitle = "Характеристики",
                accentColor = Color(0xFF8E24AA),
                onClick = { onNavigate(Screen.Compare.route) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── AI Section ───────────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            onClick = { onNavigate(Screen.ModelManager.route) }
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "ИИ Эксперт (Online)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Чат и диагностика Gemini/Grok",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
