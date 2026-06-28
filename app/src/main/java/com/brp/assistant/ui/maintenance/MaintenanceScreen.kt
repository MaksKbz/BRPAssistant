package com.brp.assistant.ui.maintenance

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brp.assistant.data.db.entities.BrpModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceScreen(
    selectedVehicle: BrpModel?,
    purchaseDate: Long?,
    onUpdatePurchaseDate: (Long) -> Unit,
    onBack: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))

    // Списки регламентов в зависимости от типа техники
    val schedule = remember(selectedVehicle) {
        val brand = selectedVehicle?.brand?.lowercase() ?: ""
        val isElectric = selectedVehicle?.isElectric == 1 || selectedVehicle?.modelName?.lowercase()?.contains("electric") == true
        
        when {
            isElectric -> listOf(
                MaintenanceInterval("Проверка батареи", 1, "Первый месяц", "Проверка состояния АКБ, контактов зарядки и ПО."),
                MaintenanceInterval("Контроль охлаждения", 6, "Каждые 6 мес.", "Проверка уровня хладагента инвертора и целостности радиатора."),
                MaintenanceInterval("Ежегодный осмотр", 12, "Раз в год", "Проверка приводного ремня/цепи, тормозной жидкости и ходовой."),
                MaintenanceInterval("Комплексное обслуживание", 24, "Раз в 2 года", "Замена охлаждающей жидкости батареи, диагностика ячеек.")
            )
            brand.contains("sea-doo") -> listOf(
                MaintenanceInterval("Первое ТО (Обкатка)", 1, "10 м/ч или 1 мес.", "Замена масла в двигателе, фильтра, проверка кольца импеллера."),
                MaintenanceInterval("Промежуточное ТО", 6, "50 м/ч или 6 мес.", "Осмотр водомета, проверка свечей, консервация узлов."),
                MaintenanceInterval("Годовое обслуживание", 12, "100 м/ч или 1 год", "Полная замена жидкостей, обслуживание iBR, проверка турбины."),
                MaintenanceInterval("Комплексное ТО", 24, "200 м/ч или 2 года", "Замена антифриза, свечей, диагностика системы охлаждения.")
            )
            brand.contains("can-am") -> listOf(
                MaintenanceInterval("Первое ТО (Обкатка)", 1, "10 м/ч или 1 мес.", "Замена масла двигателя и редукторов, проверка затяжки болтов."),
                MaintenanceInterval("Сезонное обслуживание", 6, "50 м/ч или 6 мес.", "Шприцевание подвески, чистка вариатора, проверка пыльников."),
                MaintenanceInterval("Основное ТО", 12, "100 м/ч или 1 год", "Замена всех масел, обслуживание вариатора, проверка DPS."),
                MaintenanceInterval("Замена ремня и ГРМ", 24, "200 м/ч или 2 года", "Профилактическая замена ремня вариатора, проверка клапанов.")
            )
            brand.contains("ski-doo") || brand.contains("lynx") -> listOf(
                MaintenanceInterval("Первое ТО (Обкатка)", 1, "10 м/ч или 1 мес.", "Проверка натяжки гусеницы, затяжка болтов, диагностика E-TEC."),
                MaintenanceInterval("Подготовка к сезону", 6, "50 м/ч или 6 мес.", "Смазка подвески, проверка склизов, замена масла в коробке."),
                MaintenanceInterval("Годовое ТО", 12, "100 м/ч или 1 год", "Чистка клапанов RAVE, проверка вариатора, замена свечей."),
                MaintenanceInterval("Большое ТО", 24, "200 м/ч или 2 года", "Замена топливных фильтров, антифриза, диагностика гусеницы.")
            )
            else -> emptyList()
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { onUpdatePurchaseDate(it) }
                    showDatePicker = false
                }) { Text("Готово", fontSize = 18.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена", fontSize = 18.sp) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Регламент обслуживания", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (selectedVehicle == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Выберите модель в разделе 'Моя техника'", fontSize = 18.sp)
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(selectedVehicle.modelName, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(selectedVehicle.brand.uppercase(), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val dateLabel = purchaseDate?.let { sdf.format(Date(it)) } ?: "Дата не установлена"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Начало эксплуатации: ", fontSize = 14.sp)
                            Text(dateLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                        ) {
                            Text("Указать дату покупки", fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("График сервисных работ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        schedule.forEach { item ->
                            val cal = Calendar.getInstance()
                            if (purchaseDate != null) {
                                cal.timeInMillis = purchaseDate
                                cal.add(Calendar.MONTH, item.months)
                            }
                            cal.set(Calendar.HOUR_OF_DAY, 10)
                            cal.set(Calendar.MINUTE, 0)
                            val startTime = cal.timeInMillis
                            cal.set(Calendar.HOUR_OF_DAY, 11)
                            val endTime = cal.timeInMillis

                            val intent = Intent(Intent.ACTION_INSERT)
                                .setData(CalendarContract.Events.CONTENT_URI)
                                .putExtra(CalendarContract.Events.TITLE, "Срочно записаться на сервис по регламенту проведения работ")
                                .putExtra(CalendarContract.Events.DESCRIPTION, "Плановое ТО для ${selectedVehicle.modelName}. ${item.description}")
                                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = purchaseDate != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Внести все графики в календарь", fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(schedule) { item ->
                        val targetDate = if (purchaseDate != null) {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = purchaseDate
                            cal.add(Calendar.MONTH, item.months)
                            cal.time
                        } else null

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(item.interval, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    
                                    if (targetDate != null) {
                                        Text("Ожидается: ${sdf.format(targetDate)}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(item.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                Surface(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        if (targetDate != null) {
                                            cal.time = targetDate
                                        }
                                        cal.set(Calendar.HOUR_OF_DAY, 10)
                                        cal.set(Calendar.MINUTE, 0)
                                        val startTime = cal.timeInMillis
                                        cal.set(Calendar.HOUR_OF_DAY, 11)
                                        val endTime = cal.timeInMillis

                                        val intent = Intent(Intent.ACTION_INSERT)
                                            .setData(CalendarContract.Events.CONTENT_URI)
                                            .putExtra(CalendarContract.Events.TITLE, "Срочно записаться на сервис по регламенту проведения работ")
                                            .putExtra(CalendarContract.Events.DESCRIPTION, "ТО: ${item.title} для ${selectedVehicle.modelName}")
                                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                                        context.startActivity(intent)
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

data class MaintenanceInterval(val title: String, val months: Int, val interval: String, val description: String)
