package dev.voltra

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

private val Background = Color(0xFF101713)
private val Card = Color(0xFF1B2620)
private val Muted = Color(0xFF9CAB9F)
private val Lime = Color(0xFFBCF36B)
private val White = Color(0xFFF0F5EC)

class MainActivity : ComponentActivity() {
    private val active = mutableStateOf(false)
    private lateinit var repository: BatteryRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = BatteryRepository(this)
        window.statusBarColor = android.graphics.Color.rgb(16, 23, 19)
        window.navigationBarColor = android.graphics.Color.rgb(16, 23, 19)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Lime, background = Background, surface = Card)) { VoltraApp(repository, active.value, this) } }
    }
    override fun onResume() { super.onResume(); BackgroundSampling.ensureScheduled(this); active.value = true }
    override fun onPause() { active.value = false; repository.pause(); super.onPause() }
    override fun onDestroy() { repository.close(); super.onDestroy() }
}

@Composable
private fun VoltraApp(repo: BatteryRepository, active: Boolean, context: android.content.Context) {
    var snapshot by remember { mutableStateOf<BatterySnapshot?>(null) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { snapshot = withContext(Dispatchers.IO) { repo.snapshot() } } }
    var design by remember { mutableStateOf(repo.designCapacity.toString().takeUnless { it == "0" } ?: "") }
    var settings by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(0) }
    var currentMultiplier by remember { mutableIntStateOf(repo.currentMultiplier) }
    var chartDays by remember { mutableIntStateOf(1) }
    var chartMetric by remember { mutableStateOf(ChartMetric.LEVEL) }
    var background by remember { mutableStateOf(BackgroundSampling.enabled(context)) }
    var backgroundScheduled by remember { mutableStateOf(BackgroundSampling.scheduled(context)) }
    var scheduleError by remember { mutableStateOf(false) }
    var notificationsBlocked by remember { mutableStateOf(false) }
    var monitor by remember { mutableStateOf(MonitorService.running) }
    var monitorError by remember { mutableStateOf(false) }
    val monitorPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsBlocked = !granted
        if (granted) { monitor = runCatching { MonitorService.start(context); true }.getOrDefault(false); monitorError = !monitor }
        else monitorError = true
    }
    var lowAlert by remember { mutableStateOf(LowBatteryAlert.enabled(context)) }
    var lowThreshold by remember { mutableIntStateOf(LowBatteryAlert.threshold(context)) }
    val lowPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        LowBatteryAlert.setEnabled(context, granted); lowAlert = granted; notificationsBlocked = !granted
    }
    var tempAlert by remember { mutableStateOf(TemperatureAlert.enabled(context)) }
    var tempThreshold by remember { mutableIntStateOf(TemperatureAlert.threshold(context)) }
    val tempPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        TemperatureAlert.setEnabled(context, granted); tempAlert = granted; notificationsBlocked = !granted
    }
    var alert by remember { mutableStateOf(ChargeAlert.enabled(context)) }
    var threshold by remember { mutableIntStateOf(ChargeAlert.threshold(context)) }
    var confirmClear by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ChargeAlert.setEnabled(context, granted); alert = granted; notificationsBlocked = !granted
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch {
                val succeeded = withContext(Dispatchers.IO) { runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.appendLine("timestamp_ms,level_percent,plugged,charging,charge_counter_uAh,temperature_c,device_interactive,plug_type,current_ua,voltage_mv")
                        repo.observations().forEach { sample ->
                            writer.appendLine("${sample.time},${sample.level},${sample.plugged},${sample.charging},${sample.counter ?: ""},${sample.temperature ?: ""},${sample.screenOn ?: ""},${sample.plugType ?: ""},${sample.currentMicroamps ?: ""},${sample.voltageMv ?: ""}")
                        }
                    } ?: error("Unable to open file")
                }.isSuccess }
                Toast.makeText(context, if (succeeded) "История сохранена" else "Не удалось сохранить историю", Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(active) {
        while (active) {
            snapshot = withContext(Dispatchers.IO) { repo.snapshot() }
            monitor = MonitorService.running
            background = BackgroundSampling.enabled(context)
            backgroundScheduled = BackgroundSampling.scheduled(context)
            val permitted = (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).areNotificationsEnabled()
            if (permitted) notificationsBlocked = false else if (alert || tempAlert || lowAlert || monitor) notificationsBlocked = true
            delay(15_000)
        }
    }
    val current = snapshot
    if (current == null) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Lime) }
        return
    }
    val reading = current.reading
    val estimate = current.estimates
    val observations = current.observations
    val sessions = current.sessions
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Очистить историю?") }, text = { Text("Все измерения и заводская ёмкость будут удалены с устройства.") }, confirmButton = { TextButton(onClick = { repo.clear(); design = ""; refresh(); confirmClear = false; settings = false }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } })
    if (settings && !confirmClear) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Настройки батареи") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Укажите заводскую ёмкость из характеристик телефона, чтобы оценить износ. Переключатели применяются сразу; ёмкость — после сохранения.", color = Muted)
            OutlinedTextField(value = design, onValueChange = { if (it.length <= 5 && it.all(Char::isDigit)) design = it }, label = { Text("Заводская ёмкость, мА·ч") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = design.isNotEmpty() && (design.toIntOrNull() ?: 0) !in 500..50000,
                supportingText = { if (design.isNotEmpty() && (design.toIntOrNull() ?: 0) !in 500..50000) Text("Введите от 500 до 50 000 мА·ч") })
            Text("Калибровка тока: ×$currentMultiplier", fontSize = 14.sp)
            Text("Меняй только если телефон неверно сообщает единицы тока. Влияет также на оценку мощности.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 10, 100, 1000).forEach { multiplier ->
                    FilterChip(selected = multiplier == currentMultiplier, onClick = { currentMultiplier = multiplier; repo.setCurrentMultiplier(multiplier); refresh() }, label = { Text("×$multiplier", fontSize = 11.sp) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Непрерывный мониторинг"); Text("Измерения примерно каждые 30 секунд, постоянное уведомление и дополнительный расход батареи. В спящем режиме возможны задержки.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp) }
                Switch(checked = monitor, onCheckedChange = { enabled ->
                    if (!enabled) { MonitorService.stop(context); monitor = false; monitorError = false }
                    else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) monitorPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else if (!(context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).areNotificationsEnabled()) { notificationsBlocked = true; monitorError = true }
                    else { monitor = runCatching { MonitorService.start(context); true }.getOrDefault(false); monitorError = !monitor }
                })
            }
            if (monitorError) Text("Не удалось запустить мониторинг. Проверь разрешение на уведомления.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Фоновые измерения"); Text("Примерно каждые 15 минут. Android может задерживать их; виджет обновляется отдельно.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp) }
                Switch(checked = background, onCheckedChange = { enabled -> background = BackgroundSampling.setEnabled(context, enabled) && enabled; backgroundScheduled = BackgroundSampling.scheduled(context); scheduleError = enabled && !background })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Напомнить при $threshold%"); Text("Во время измерений; фоновое уведомление может задержаться. Зарядка не останавливается.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp) }
                Switch(checked = alert, onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { ChargeAlert.setEnabled(context, enabled); alert = enabled }
                })
            }
            if (alert) Slider(value = threshold.toFloat(), onValueChange = { threshold = (kotlin.math.round(it / 5) * 5).toInt() }, onValueChangeFinished = { ChargeAlert.setThreshold(context, threshold) }, valueRange = 60f..100f, steps = 7)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Температура от $tempThreshold °C"); Text("Оповещение по датчику при следующем измерении.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp) }
                Switch(checked = tempAlert, onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) tempPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { TemperatureAlert.setEnabled(context, enabled); tempAlert = enabled }
                })
            }
            if (tempAlert) Slider(value = tempThreshold.toFloat(), onValueChange = { tempThreshold = kotlin.math.round(it).toInt() }, onValueChangeFinished = { TemperatureAlert.setThreshold(context, tempThreshold) }, valueRange = 35f..50f, steps = 14)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Низкий заряд: $lowThreshold%"); Text("Оповещение при разряде; возможно с задержкой.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp) }
                Switch(checked = lowAlert, onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) lowPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { LowBatteryAlert.setEnabled(context, enabled); lowAlert = enabled }
                })
            }
            if (lowAlert) Slider(value = lowThreshold.toFloat(), onValueChange = { lowThreshold = (kotlin.math.round(it / 5) * 5).toInt() }, onValueChangeFinished = { LowBatteryAlert.setThreshold(context, lowThreshold) }, valueRange = 5f..30f, steps = 4)
            if (notificationsBlocked) {
                Text("Разреши уведомления в настройках телефона, затем включи нужное оповещение здесь.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                TextButton(onClick = {
                    val intent = if (Build.VERSION.SDK_INT >= 26) android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }) { Text("Настройки уведомлений") }
            }
            if (scheduleError) Text("Не удалось включить фоновые измерения", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            TextButton(onClick = { confirmClear = true }) { Text("Очистить данные") }
        }
    }, confirmButton = { TextButton(enabled = design.isEmpty() || (design.toIntOrNull() ?: 0) in 500..50000, onClick = { repo.setDesign(design.toIntOrNull() ?: 0); refresh(); settings = false }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = { settings = false }) { Text("Закрыть") } })
    Scaffold(containerColor = Background, bottomBar = {
        NavigationBar(containerColor = Color(0xFF162019), contentColor = White) {
            NavigationBarItem(selected = selected == 0, onClick = { selected = 0 }, icon = { Icon(Icons.Rounded.Bolt, null) }, label = { Text("Обзор") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF354A2C), selectedIconColor = Lime))
            NavigationBarItem(selected = selected == 2, onClick = { selected = 2 }, icon = { Icon(Icons.Rounded.Timeline, null) }, label = { Text("История") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF354A2C), selectedIconColor = Lime))
            NavigationBarItem(selected = selected == 1, onClick = { selected = 1 }, icon = { Icon(Icons.Rounded.Favorite, null) }, label = { Text("Здоровье") }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF354A2C), selectedIconColor = Lime))
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("VOLTRA", color = Lime, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp); Text(when (selected) { 0 -> "Твоя батарея"; 1 -> "Здоровье батареи"; else -> "История батареи" }, color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold) }
                IconButton(onClick = { settings = true }, modifier = Modifier.background(Card, RoundedCornerShape(16.dp))) { Icon(Icons.Rounded.Settings, "Настройки", tint = White) }
            }
            if (selected == 0) {
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Label("ПРЯМО СЕЙЧАС"); Spacer(Modifier.height(8.dp)); Text(if (reading.full && reading.plugged) "Заряжено" else if (reading.charging) "Заряжается" else if (reading.plugged) "Подключено" else "На батарее", color = White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold); Text(reading.source, color = Muted, fontSize = 14.sp) }
                        Text("● LIVE", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFF30432B), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(205.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(182.dp)) {
                            val stroke = 13.dp.toPx(); val inset = stroke / 2
                            drawArc(Color(0xFF334238), 135f, 270f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                            if (reading.level >= 0) drawArc(Lime, 135f, 270f * reading.level.coerceIn(0, 100) / 100f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (reading.level >= 0) "${reading.level}%" else "—", color = White, fontSize = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp); Text("ЗАРЯД", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp) }
                    }
                    HorizontalDivider(color = Color(0xFF344239))
                    Spacer(Modifier.height(16.dp))
                    Label(if (reading.plugged) "ДО ПОЛНОГО ЗАРЯДА" else "ПРИМЕРНО ОСТАЛОСЬ")
                    Spacer(Modifier.height(5.dp))
                    Text((if (reading.full && reading.plugged) 0 else if (reading.plugged) reading.chargeMinutes ?: estimate.chargeRemaining else estimate.remainingMinutes)?.let { formatEstimate(it) } ?: "Пока недостаточно данных", color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (!reading.plugged) "По последним измеренным разрядам" else if (reading.chargeMinutes != null || reading.full) "Оценка Android" else if (estimate.chargeRemaining != null) "По текущей скорости; не учитывает замедление и лимит заряда" else "Android ещё не дал оценку", color = Muted, fontSize = 13.sp)
                }
                SectionTitle("Показатели", "С устройства")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(Modifier.weight(1f), "Температура", reading.temperature?.let { String.format(Locale.getDefault(), "%.1f °C", it) } ?: "—", "Термодатчик")
                    Metric(Modifier.weight(1f), "Напряжение", reading.voltage?.let { String.format(Locale.getDefault(), "%.2f В", it / 1000f) } ?: "—", "Сейчас")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(Modifier.weight(1f), "Ток", reading.current?.let { formatCurrent(it) } ?: "—", "Знак устройства")
                    Metric(Modifier.weight(1f), "Состояние", reading.status, "По данным Android")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(Modifier.weight(1f), "Мощность", if (reading.current != null && reading.voltage != null) String.format(Locale.getDefault(), "%.1f Вт", abs(reading.current / 1_000_000f * reading.voltage / 1000f)) else "—", "На батарее, оценка")
                    Metric(Modifier.weight(1f), "Источник", reading.source, "Тип подключения")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(Modifier.weight(1f), "Средний ток", reading.averageCurrent?.let { formatCurrent(it) } ?: "—", "По данным системы")
                    Metric(Modifier.weight(1f), "Энергия", reading.energyNwh?.let { String.format(Locale.getDefault(), "%.2f Вт·ч", it / 1_000_000_000.0) } ?: "—", "Остаток")
                }
                Note("Мощность рассчитана по току и напряжению батареи; это не мощность адаптера. Прогноз появится после 10 минут и снижения заряда на 2% в измеренной сессии. Он зависит от того, как ты пользуешься телефоном.")
                val currentSession = sessions.lastOrNull()?.takeIf { it.start.plugged == reading.plugged && it.charging == reading.charging && reading.time - it.end.time in 0..75 * 60_000L }
                if (currentSession != null) {
                    SectionTitle("Текущая сессия", if (reading.charging) "Зарядка" else if (reading.plugged) "Подключено" else "Разряд")
                    Panel {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Label("С НАЧАЛА НАБЛЮДЕНИЯ")
                                Spacer(Modifier.height(5.dp))
                                Text("${currentSession.start.level}% → ${reading.level}%", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(duration(reading.time - currentSession.start.time), color = Lime, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Начало: ${formatTime(currentSession.start.time)} · ${currentSession.sampleCount} измер.", color = Muted, fontSize = 12.sp)
                    }
                }
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Label("СБОР ДАННЫХ")
                            Spacer(Modifier.height(5.dp))
                            Text(if (monitor) "Непрерывный мониторинг" else if (background && backgroundScheduled) "Фоновые измерения" else "При открытом приложении", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(observations.lastOrNull()?.let { "Последняя точка: ${formatTime(it.time)}" } ?: "Измерений пока нет", color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { settings = true }) { Text("Настроить") }
                    }
                }
                SectionTitle("Прогноз по экрану", "Примерно")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val onEstimate = estimate.screenOnMinutes
                    val offEstimate = estimate.screenOffMinutes
                    Metric(Modifier.weight(1f), "Экран включён", onEstimate?.let { formatEstimate(it) } ?: "—", "По замерам")
                    Metric(Modifier.weight(1f), "Экран выключен", offEstimate?.let { formatEstimate(it) } ?: "—", "По замерам")
                }
                Note("Используется интерактивность устройства по Android. Между замерами экран мог переключаться, а данные — отличаться от фактического состояния дисплея.")
            } else if (selected == 1) {
                Panel {
                    Label("ОЦЕНКА ЁМКОСТИ")
                    Spacer(Modifier.height(12.dp))
                    Text(estimate.healthPercent?.let { "$it%" } ?: "—", color = Lime, fontSize = 58.sp, fontWeight = FontWeight.Bold)
                    Text("от заводской ёмкости", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Подходящих зарядок: ${sessions.count { it.capacity != null }}. Взвешенная медиана по диапазону заряда для последних 10. Оценка по счётчику заряда может отличаться от реальной ёмкости.", color = Muted, lineHeight = 20.sp, fontSize = 14.sp)
                    if ((estimate.healthPercent ?: 0) > 100) Text("Оценка выше 100%: проверь заводскую ёмкость и повтори измерение.", color = Color(0xFFFFC182), lineHeight = 20.sp, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(Modifier.weight(1f), "Циклы", reading.cycles?.toString() ?: "—", "По данным Android")
                    Metric(Modifier.weight(1f), "Износ", estimate.healthPercent?.takeIf { it in 0..100 }?.let { "${100 - it}%" } ?: "—", "Приблизительно")
                }
                Text("Счётчик заряда: ${reading.counter?.let { "${it / 1000} мА·ч" } ?: "не поддерживается"}", color = Muted, fontSize = 13.sp)
                SectionTitle("Детали", "Оценка")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(Modifier.weight(1f), "Измерено", estimate.capacityMah?.let { "$it мА·ч" } ?: "—", "Ёмкость")
                    Metric(Modifier.weight(1f), "Заводская", repo.designCapacity.takeIf { it > 0 }?.let { "$it мА·ч" } ?: "—", "Из настроек")
                }
                Panel {
                    Label("ИСТОРИЯ ЁМКОСТИ")
                    val capacities = sessions.mapNotNull { it.capacity }.takeLast(10)
                    Spacer(Modifier.height(12.dp))
                    if (capacities.size < 2) Text("График появится после двух подходящих зарядок.", color = Muted, fontSize = 14.sp)
                    else {
                        CapacityChart(capacities)
                        Spacer(Modifier.height(8.dp))
                        Text("Диапазон: ${capacities.min()}–${capacities.max()} мА·ч · ${capacities.size} измер.", color = Muted, fontSize = 12.sp)
                    }
                }
                Panel {
                    Text("Как получить оценку", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text("1. Укажи заводскую ёмкость в настройках.\n\n2. Заряди телефон минимум на 10% за 5 минут или дольше, пока Voltra собирает измерения.\n\n3. Если телефон поддерживает счётчик заряда, здесь появится оценка.", color = Muted, lineHeight = 21.sp, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { settings = true }) { Text("Указать ёмкость →") }
                }
                SectionTitle("Устройство", "Android")
                Panel {
                    DetailLine("Модель", "${Build.MANUFACTURER} ${Build.MODEL}")
                    DetailLine("Версия Android", Build.VERSION.RELEASE ?: "—")
                    DetailLine("Химия батареи", reading.technology ?: "Нет данных")
                    DetailLine("Статус батареи", reading.status)
                }
                Note("Состояние Android и процент остаточной ёмкости — разные показатели. Фоновые измерения можно включить в настройках.")
            } else {
                Panel {
                    Label(chartMetric.title)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChartMetric.entries.forEach { metric -> FilterChip(selected = metric == chartMetric, onClick = { chartMetric = metric }, label = { Text(metric.label) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = chartDays == 1, onClick = { chartDays = 1 }, label = { Text("24 ч") })
                        FilterChip(selected = chartDays == 7, onClick = { chartDays = 7 }, label = { Text("7 дн") })
                    }
                    Spacer(Modifier.height(12.dp))
                    val points = remember(current, chartDays) { observations.filter { it.time in (System.currentTimeMillis() - chartDays * 24 * 60 * 60_000L)..System.currentTimeMillis() } }
                    val valid = remember(points, chartMetric) { points.filter { chartMetric.value(it) != null } }
                    if (valid.size < 2) {
                        Text("График появится после нескольких измерений", color = Muted, modifier = Modifier.padding(vertical = 35.dp))
                    } else {
                        HistoryChart(points, chartDays, chartMetric)
                        Spacer(Modifier.height(8.dp))
                        val values = valid.mapNotNull { chartMetric.value(it) }
                        val (low, high) = chartMetric.bounds(values)
                        Text(String.format(Locale.getDefault(), "Шкала %.1f–%.1f %s · последнее: %s", low, high, chartMetric.unit, formatTime(valid.last().time)), color = Muted, fontSize = 12.sp)
                        if (chartMetric == ChartMetric.CURRENT) Text("Сохранён с калибровкой, действовавшей в момент замера.", color = Muted, fontSize = 12.sp)
                    }
                }
                SectionTitle("Скорость", "По сохранённым сессиям")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(Modifier.weight(1f), "Зарядка", estimate.chargeSpeed?.let { String.format(Locale.getDefault(), "%.1f%%/ч", it) } ?: "—", "В среднем")
                    Metric(Modifier.weight(1f), "Разряд", estimate.dischargeSpeed?.let { String.format(Locale.getDefault(), "%.1f%%/ч", it) } ?: "—", "В среднем")
                }
                if (estimate.observedScreenOn > 0 || estimate.observedScreenOff > 0) {
                    SectionTitle("Экран", "Наблюдалось за 24 часа")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Metric(Modifier.weight(1f), "Включён", formatEstimate(estimate.observedScreenOn), "Приблизительно")
                        Metric(Modifier.weight(1f), "Выключен", formatEstimate(estimate.observedScreenOff), "Приблизительно")
                    }
                    Note("Интерактивность по Android учитывается только на границах интервалов. Переключения и пробелы между замерами могут влиять на итог.")
                }
                if (estimate.sourceSpeeds.isNotEmpty()) {
                    Panel {
                        Text("По источникам", color = White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        estimate.sourceSpeeds.forEach { (type, speed) ->
                            DetailLine(when(type) { 1 -> "Сеть"; 2 -> "USB"; 4 -> "Беспроводная"; else -> "Док-станция" }, String.format(Locale.getDefault(), "%.1f%%/ч", speed))
                        }
                        Text("Средняя скорость по наблюдаемым сессиям; зависит от уровня заряда и нагрузки.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
                SectionTitle("Сессии", "До 30 дней")
                val recent = sessions.filter { it.sampleCount >= 2 }.takeLast(20).reversed()
                if (recent.isEmpty()) Note("Здесь появятся зарядки и разряды после нескольких измерений.")
                recent.forEach { session ->
                    Panel {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (session.charging) "Зарядка" else if (session.start.plugged) "Подключено" else "Разряд", color = White, fontWeight = FontWeight.SemiBold)
                            Text("${session.start.level}% → ${session.end.level}%", color = Lime, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${formatTime(session.start.time)} · ${duration(session.end.time - session.start.time)} · ${session.sampleCount} измер.", color = Muted, fontSize = 12.sp)
                        val deltaCounter = if (session.start.counter != null && session.end.counter != null) (session.end.counter - session.start.counter) / 1000 else null
                        val details = buildList {
                            if (session.start.plugged) session.start.plugType?.let { type -> add(when(type) { 1 -> "Сеть"; 2 -> "USB"; 4 -> "Беспроводная"; 8 -> "Док-станция"; else -> "Источник неизвестен" }) }
                            deltaCounter?.let { add("Δ счётчика ${if (it > 0) "+" else ""}$it мА·ч") }
                            session.peakTemperature?.let { add(String.format(Locale.getDefault(), "макс. по точкам %.1f °C", it)) }
                        }
                        if (details.isNotEmpty()) Text(details.joinToString(" · "), color = Muted, fontSize = 12.sp)
                        session.capacity?.let { Text("Оценка ёмкости: $it мА·ч", color = Muted, fontSize = 12.sp) }
                        session.capacityReason?.let { Text("Не включена в здоровье: $it", color = Muted, fontSize = 12.sp) }
                    }
                }
                OutlinedButton(onClick = { export.launch("voltra-history.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Экспортировать историю CSV") }
                Note("История хранит до 30 дней и 20 000 точек. Между точками более 75 минут начинается новая сессия. Фоновый сбор приблизителен: Android может задерживать или пропускать измерения.")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(24.dp)).padding(20.dp), content = content) }
@Composable private fun Label(value: String) { Text(value, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp) }
@Composable private fun SectionTitle(title: String, subtitle: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) { Text(title, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 12.sp) } }
@Composable private fun Metric(modifier: Modifier, title: String, value: String, hint: String) { Column(modifier.background(Card, RoundedCornerShape(22.dp)).height(125.dp).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) { Text(title, color = Muted, fontSize = 13.sp); Text(value, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(hint, color = Muted, fontSize = 11.sp) } }
@Composable private fun Note(value: String) { Text(value, color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp)) }

private fun formatTime(time: Long): String = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(time))
private fun formatCurrent(microamps: Int): String {
    if (microamps == 0) return "0 мА"
    if (kotlin.math.abs(microamps.toLong()) < 10_000) return String.format(Locale.getDefault(), "%+.1f мА", microamps / 1000.0)
    val ma = microamps / 1000
    return "${if (ma > 0) "+" else ""}$ma мА"
}
private fun formatEstimate(minutes: Int): String = if (minutes >= 7 * 24 * 60) "7 дней и более" else "${minutes / 60} ч ${minutes % 60} мин"
private fun duration(ms: Long): String { val minutes = (ms / 60_000).coerceAtLeast(0); return "${minutes / 60} ч ${minutes % 60} мин" }
private enum class ChartMetric(val title: String, val label: String, val unit: String, val color: Color) {
    LEVEL("ЗАРЯД", "Заряд", "%", Lime),
    TEMPERATURE("ТЕМПЕРАТУРА", "°C", "°C", Color(0xFFFFB47D)),
    CURRENT("ТОК", "Ток", "мА", Color(0xFF9DDFF0)),
    VOLTAGE("НАПРЯЖЕНИЕ", "Вольтаж", "В", Color(0xFFC4BAF8));
    fun value(point: Observation): Float? = when (this) {
        LEVEL -> point.level.toFloat()
        TEMPERATURE -> point.temperature
        CURRENT -> point.currentMicroamps?.div(1000f)
        VOLTAGE -> point.voltageMv?.div(1000f)
    }
    fun bounds(values: List<Float>): Pair<Float, Float> {
        if (this == LEVEL) return 0f to 100f
        val min = values.min(); val max = values.max()
        val padding = maxOf((max - min) * .1f, when (this) { TEMPERATURE -> 2f; CURRENT -> 50f; VOLTAGE -> .1f; else -> 1f })
        return (min - padding) to (max + padding)
    }
}
@Composable private fun HistoryChart(points: List<Observation>, days: Int, metric: ChartMetric) {
    val values = points.mapNotNull { metric.value(it) }
    val description = "${metric.title}, $days дн., последнее значение ${values.last()} ${metric.unit}"
    Canvas(Modifier.fillMaxWidth().height(130.dp).semantics { contentDescription = description }) {
        val span = days * 24 * 60 * 60_000L
        val earliest = System.currentTimeMillis() - span
        val (low, high) = metric.bounds(values)
        fun point(item: Observation, value: Float) = Offset(((item.time - earliest).toFloat() / span).coerceIn(0f, 1f) * size.width, size.height * (1f - ((value - low) / (high - low)).coerceIn(0f, 1f)))
        for (fraction in listOf(0f, .5f, 1f)) drawLine(Color(0xFF344239), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), strokeWidth = 1.dp.toPx())
        points.zipWithNext().forEach { (a, b) ->
            val av = metric.value(a); val bv = metric.value(b)
            if (av != null && bv != null && b.time - a.time in 1..75 * 60_000L) drawLine(metric.color, point(a, av), point(b, bv), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        points.forEachIndexed { index, item ->
            val value = metric.value(item) ?: return@forEachIndexed
            val previous = points.getOrNull(index - 1)
            val next = points.getOrNull(index + 1)
            val joinedBefore = previous != null && metric.value(previous) != null && item.time - previous.time in 1..75 * 60_000L
            val joinedAfter = next != null && metric.value(next) != null && next.time - item.time in 1..75 * 60_000L
            if (points.size <= 200 || (!joinedBefore && !joinedAfter)) drawCircle(metric.color, 2.dp.toPx(), point(item, value))
        }
    }
}

@Composable private fun CapacityChart(values: List<Int>) {
    val description = "История оценок ёмкости, последнее значение ${values.last()} миллиампер-часов"
    Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = description }) {
        val low = values.min() * .9f
        val high = (values.max() * 1.1f).coerceAtLeast(low + 1)
        val points = values.mapIndexed { index, value -> Offset(size.width * index / (values.size - 1), size.height * (1 - (value - low) / (high - low))) }
        for (fraction in listOf(0f, .5f, 1f)) drawLine(Color(0xFF344239), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), strokeWidth = 1.dp.toPx())
        points.zipWithNext().forEach { (a, b) -> drawLine(Lime, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round) }
        points.forEach { drawCircle(Lime, 3.dp.toPx(), it) }
    }
}

@Composable private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
