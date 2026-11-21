package dev.voltra.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.voltra.AppPreferences
import dev.voltra.BackgroundSampling
import dev.voltra.BatteryRepository
import dev.voltra.BatterySnapshot
import dev.voltra.ChargeAlert
import dev.voltra.CsvExport
import dev.voltra.LowBatteryAlert
import dev.voltra.MonitorService
import dev.voltra.R
import dev.voltra.TemperatureAlert
import dev.voltra.TemperatureUnit
import dev.voltra.ThemeMode
import dev.voltra.ui.components.ChartMetric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

enum class NavDestination {
    OVERVIEW,
    HISTORY,
    HEALTH,
    SETTINGS,
}

data class VoltraUiState(
    val snapshot: BatterySnapshot? = null,
    val selectedTab: NavDestination = NavDestination.OVERVIEW,
    val isRefreshing: Boolean = false,
    val retentionBusy: Boolean = false,
    val designCapacity: Int = 0,
    val designInput: String = "",
    val currentMultiplier: Int = 1,
    val chartDays: Int = 1,
    val chartMetric: ChartMetric = ChartMetric.LEVEL,
    val visibleSessions: Int = 20,
    val backgroundEnabled: Boolean = false,
    val backgroundScheduled: Boolean = false,
    val scheduleError: Boolean = false,
    val monitorRunning: Boolean = false,
    val monitorError: Boolean = false,
    val notificationsBlocked: Boolean = false,
    val chargeAlertEnabled: Boolean = false,
    val chargeAlertThreshold: Int = 80,
    val tempAlertEnabled: Boolean = false,
    val tempAlertThreshold: Int = 40,
    val lowAlertEnabled: Boolean = false,
    val lowAlertThreshold: Int = 20,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val setupDismissed: Boolean = false,
    val confirmClearDialog: Boolean = false,
    val pendingRetentionDays: Int? = null,
)

class VoltraViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application
    private val repository: BatteryRepository = BatteryRepository.getInstance(app)
    private val snapshotRevision = AtomicInteger(0)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<VoltraUiState> = _uiState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            MonitorService.isRunning.collect { running ->
                _uiState.update { state ->
                    state.copy(
                        monitorRunning = running,
                        monitorError = if (running) false else state.monitorError,
                    )
                }
            }
        }
        refresh()
    }

    private fun loadInitialState(): VoltraUiState {
        val design = repository.designCapacity
        return VoltraUiState(
            designCapacity = design,
            designInput = design.takeIf { it > 0 }?.toString().orEmpty(),
            currentMultiplier = repository.currentMultiplier,
            backgroundEnabled = BackgroundSampling.enabled(app),
            backgroundScheduled = BackgroundSampling.scheduled(app),
            monitorRunning = MonitorService.isRunning.value,
            chargeAlertEnabled = ChargeAlert.enabled(app),
            chargeAlertThreshold = ChargeAlert.threshold(app),
            tempAlertEnabled = TemperatureAlert.enabled(app),
            tempAlertThreshold = TemperatureAlert.threshold(app),
            lowAlertEnabled = LowBatteryAlert.enabled(app),
            lowAlertThreshold = LowBatteryAlert.threshold(app),
            themeMode = AppPreferences.themeMode(app),
            temperatureUnit = AppPreferences.temperatureUnit(app),
            setupDismissed = AppPreferences.isSetupDismissed(app),
        )
    }

    fun selectTab(destination: NavDestination) {
        _uiState.update { it.copy(selectedTab = destination) }
    }

    fun selectChartMetric(metric: ChartMetric) {
        _uiState.update { it.copy(chartMetric = metric) }
    }

    fun selectChartDays(days: Int) {
        _uiState.update { it.copy(chartDays = days) }
    }

    fun showMoreSessions() {
        _uiState.update { it.copy(visibleSessions = it.visibleSessions + 20) }
    }

    fun onDesignInputChanged(value: String) {
        if (value.length <= 5 && value.all(Char::isDigit)) {
            _uiState.update { it.copy(designInput = value) }
        }
    }

    fun dismissSetupCard() {
        AppPreferences.setSetupDismissed(app, true)
        _uiState.update { it.copy(setupDismissed = true) }
    }

    fun requestClearHistory() {
        _uiState.update { it.copy(confirmClearDialog = true) }
    }

    fun dismissClearHistory() {
        _uiState.update { it.copy(confirmClearDialog = false) }
    }

    fun requestRetentionChange(days: Int) {
        val currentDays = _uiState.value.snapshot?.retentionDays ?: return
        if (days == currentDays) return
        if (days < currentDays) {
            _uiState.update { it.copy(pendingRetentionDays = days) }
        } else {
            updateRetention(days)
        }
    }

    fun dismissRetentionDialog() {
        _uiState.update { it.copy(pendingRetentionDays = null) }
    }

    fun confirmRetentionChange() {
        val days = _uiState.value.pendingRetentionDays ?: return
        _uiState.update { it.copy(pendingRetentionDays = null) }
        updateRetention(days)
    }

    fun syncSystemStatus() {
        val bgEnabled = BackgroundSampling.enabled(app)
        val bgScheduled = BackgroundSampling.scheduled(app)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val permitted = manager.areNotificationsEnabled()
        _uiState.update { state ->
            val anyNotificationFeature =
                state.chargeAlertEnabled || state.tempAlertEnabled || state.lowAlertEnabled || state.monitorRunning
            state.copy(
                backgroundEnabled = bgEnabled,
                backgroundScheduled = bgScheduled,
                monitorRunning = MonitorService.isRunning.value,
                notificationsBlocked = if (permitted) false else (state.notificationsBlocked || anyNotificationFeature),
            )
        }
    }

    fun refresh(userInitiated: Boolean = false) {
        if (_uiState.value.retentionBusy) return
        val revision = snapshotRevision.get()
        if (userInitiated) {
            _uiState.update { it.copy(isRefreshing = true) }
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.snapshot() } }
            result.onSuccess { snapshot ->
                if (revision == snapshotRevision.get() && !_uiState.value.retentionBusy) {
                    _uiState.update { state ->
                        val adjustedDays = if (state.chartDays > snapshot.retentionDays) {
                            7.coerceAtMost(snapshot.retentionDays)
                        } else {
                            state.chartDays
                        }
                        state.copy(
                            snapshot = snapshot,
                            chartDays = adjustedDays,
                            isRefreshing = false,
                        )
                    }
                } else if (userInitiated) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }.onFailure {
                if (userInitiated) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
            syncSystemStatus()
        }
    }

    fun updateDesignCapacity() {
        val input = _uiState.value.designInput
        val parsed = if (input.isEmpty()) 0 else input.toIntOrNull() ?: return
        if (parsed != 0 && parsed !in 500..50000) return
        snapshotRevision.incrementAndGet()
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                repository.setDesign(parsed)
                repository.snapshot()
            }
            _uiState.update { state ->
                state.copy(
                    designCapacity = parsed,
                    designInput = parsed.takeIf { it > 0 }?.toString().orEmpty(),
                    snapshot = snapshot,
                )
            }
            _snackbarMessages.tryEmit(app.getString(R.string.msg_design_saved))
        }
    }

    fun setCurrentMultiplier(multiplier: Int) {
        if (multiplier !in BatteryRepository.MULTIPLIERS) return
        snapshotRevision.incrementAndGet()
        _uiState.update { it.copy(currentMultiplier = multiplier) }
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                repository.setCurrentMultiplier(multiplier)
                repository.snapshot()
            }
            _uiState.update { it.copy(snapshot = snapshot) }
        }
    }

    fun updateRetention(days: Int) {
        _uiState.update { it.copy(retentionBusy = true) }
        snapshotRevision.incrementAndGet()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.setRetentionDaysAndSnapshot(days) }
            }
            snapshotRevision.incrementAndGet()
            result.onSuccess { snapshot ->
                _uiState.update { state ->
                    val adjustedDays = if (state.chartDays > snapshot.retentionDays) {
                        7.coerceAtMost(snapshot.retentionDays)
                    } else {
                        state.chartDays
                    }
                    state.copy(
                        snapshot = snapshot,
                        chartDays = adjustedDays,
                        retentionBusy = false,
                    )
                }
                _snackbarMessages.tryEmit(app.getString(R.string.msg_retention_updated, days))
            }.onFailure {
                _uiState.update { it.copy(retentionBusy = false) }
                _snackbarMessages.tryEmit(app.getString(R.string.msg_retention_failed))
            }
        }
    }

    fun clearHistory() {
        _uiState.update { it.copy(confirmClearDialog = false) }
        snapshotRevision.incrementAndGet()
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                repository.clear()
                repository.snapshot()
            }
            _uiState.update { state ->
                state.copy(
                    designCapacity = 0,
                    designInput = "",
                    snapshot = snapshot,
                    visibleSessions = 20,
                )
            }
            _snackbarMessages.tryEmit(app.getString(R.string.msg_history_cleared))
        }
    }

    fun setBackgroundSampling(enabled: Boolean) {
        viewModelScope.launch {
            val (active, scheduled) = withContext(Dispatchers.IO) {
                val ok = BackgroundSampling.setEnabled(app, enabled) && enabled
                ok to BackgroundSampling.scheduled(app)
            }
            _uiState.update { state ->
                state.copy(
                    backgroundEnabled = active,
                    backgroundScheduled = scheduled,
                    scheduleError = enabled && !active,
                )
            }
        }
    }

    fun setMonitoring(enabled: Boolean) {
        if (!enabled) {
            MonitorService.stop(app)
            _uiState.update { it.copy(monitorRunning = false, monitorError = false) }
            return
        }
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) {
            _uiState.update { it.copy(notificationsBlocked = true, monitorError = true) }
            return
        }
        val started = runCatching {
            MonitorService.start(app)
            true
        }.getOrDefault(false)
        _uiState.update { it.copy(monitorRunning = started, monitorError = !started) }
    }

    fun onMonitorPermissionResult(granted: Boolean) {
        if (granted) {
            _uiState.update { it.copy(notificationsBlocked = false) }
            setMonitoring(true)
        } else {
            _uiState.update { it.copy(notificationsBlocked = true, monitorError = true) }
        }
    }

    fun setChargeAlertEnabled(enabled: Boolean) {
        ChargeAlert.setEnabled(app, enabled)
        _uiState.update { it.copy(chargeAlertEnabled = enabled) }
    }

    fun onChargeAlertPermissionResult(granted: Boolean) {
        ChargeAlert.setEnabled(app, granted)
        _uiState.update { it.copy(chargeAlertEnabled = granted, notificationsBlocked = !granted) }
    }

    fun previewChargeAlertThreshold(threshold: Int) {
        _uiState.update { it.copy(chargeAlertThreshold = threshold.coerceIn(60, 100)) }
    }

    fun commitChargeAlertThreshold() {
        ChargeAlert.setThreshold(app, _uiState.value.chargeAlertThreshold)
    }

    fun setTempAlertEnabled(enabled: Boolean) {
        TemperatureAlert.setEnabled(app, enabled)
        _uiState.update { it.copy(tempAlertEnabled = enabled) }
    }

    fun onTempAlertPermissionResult(granted: Boolean) {
        TemperatureAlert.setEnabled(app, granted)
        _uiState.update { it.copy(tempAlertEnabled = granted, notificationsBlocked = !granted) }
    }

    fun previewTempAlertThreshold(thresholdC: Int) {
        _uiState.update { it.copy(tempAlertThreshold = thresholdC.coerceIn(35, 50)) }
    }

    fun commitTempAlertThreshold() {
        TemperatureAlert.setThreshold(app, _uiState.value.tempAlertThreshold)
    }

    fun setLowAlertEnabled(enabled: Boolean) {
        LowBatteryAlert.setEnabled(app, enabled)
        _uiState.update { it.copy(lowAlertEnabled = enabled) }
    }

    fun onLowAlertPermissionResult(granted: Boolean) {
        LowBatteryAlert.setEnabled(app, granted)
        _uiState.update { it.copy(lowAlertEnabled = granted, notificationsBlocked = !granted) }
    }

    fun previewLowAlertThreshold(threshold: Int) {
        _uiState.update { it.copy(lowAlertThreshold = threshold.coerceIn(5, 30)) }
    }

    fun commitLowAlertThreshold() {
        LowBatteryAlert.setThreshold(app, _uiState.value.lowAlertThreshold)
    }

    fun setThemeMode(mode: ThemeMode) {
        AppPreferences.setThemeMode(app, mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        AppPreferences.setTemperatureUnit(app, unit)
        _uiState.update { it.copy(temperatureUnit = unit) }
        // Refresh widget immediately so its unit updates.
        refresh()
    }

    fun notifyEmptyExport() {
        _snackbarMessages.tryEmit(app.getString(R.string.msg_export_empty))
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                runCatching {
                    val data = repository.observations()
                    app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        CsvExport.write(writer, data)
                    } ?: error("Unable to open output stream")
                }.isSuccess
            }
            _snackbarMessages.tryEmit(
                app.getString(if (succeeded) R.string.msg_export_success else R.string.msg_export_failed),
            )
        }
    }
}
