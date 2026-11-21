package dev.voltra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.voltra.BatteryRepository
import dev.voltra.BuildConfig
import dev.voltra.HistoryStore
import dev.voltra.R
import dev.voltra.TemperatureUnit
import dev.voltra.ThemeMode
import dev.voltra.ui.VoltraUiState
import dev.voltra.ui.components.Panel
import dev.voltra.ui.components.SectionLabel
import kotlin.math.round

@Composable
fun SettingsScreen(
    state: VoltraUiState,
    retentionDays: Int,
    onDesignInputChanged: (String) -> Unit,
    onSaveDesign: () -> Unit,
    onSelectMultiplier: (Int) -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onToggleBackground: (Boolean) -> Unit,
    onToggleChargeAlert: (Boolean) -> Unit,
    onPreviewChargeThreshold: (Int) -> Unit,
    onCommitChargeThreshold: () -> Unit,
    onToggleTempAlert: (Boolean) -> Unit,
    onPreviewTempThreshold: (Int) -> Unit,
    onCommitTempThreshold: () -> Unit,
    onToggleLowAlert: (Boolean) -> Unit,
    onPreviewLowThreshold: (Int) -> Unit,
    onCommitLowThreshold: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectTemperatureUnit: (TemperatureUnit) -> Unit,
    onSelectRetention: (Int) -> Unit,
    onExportCsv: () -> Unit,
    onRequestClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val designValid = state.designInput.isEmpty() || (state.designInput.toIntOrNull() ?: 0) in 500..50000
    val designChanged = (state.designInput.toIntOrNull() ?: 0) != state.designCapacity

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Battery & Calibration
            Panel {
                SectionLabel(stringResource(R.string.settings_group_battery).uppercase())
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_design_helper),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = state.designInput,
                        onValueChange = onDesignInputChanged,
                        label = { Text(stringResource(R.string.settings_design_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !designValid,
                        supportingText = if (!designValid) {
                            { Text(stringResource(R.string.settings_design_error)) }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onSaveDesign,
                        enabled = designValid && designChanged,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_multiplier_title, state.currentMultiplier),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_multiplier_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BatteryRepository.MULTIPLIERS.forEach { multiplier ->
                        FilterChip(
                            selected = multiplier == state.currentMultiplier,
                            onClick = { onSelectMultiplier(multiplier) },
                            label = { Text("×$multiplier") },
                        )
                    }
                }
            }

            // 2. Monitoring
            Panel {
                SectionLabel(stringResource(R.string.settings_group_monitoring).uppercase())
                Spacer(Modifier.height(12.dp))
                SettingToggleRow(
                    title = stringResource(R.string.settings_continuous_title),
                    description = stringResource(R.string.settings_continuous_desc),
                    checked = state.monitorRunning,
                    onCheckedChange = onToggleMonitoring,
                )
                if (state.monitorError) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_continuous_error),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))
                SettingToggleRow(
                    title = stringResource(R.string.settings_background_title),
                    description = stringResource(R.string.settings_background_desc),
                    checked = state.backgroundEnabled,
                    onCheckedChange = onToggleBackground,
                )
                if (state.scheduleError) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_background_error),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                    )
                }
            }

            // 3. Alerts
            Panel {
                SectionLabel(stringResource(R.string.settings_group_alerts).uppercase())
                Spacer(Modifier.height(12.dp))
                if (state.notificationsBlocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_notifications_blocked_title),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_notifications_blocked_body),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onOpenNotificationSettings) {
                            Text(
                                text = stringResource(R.string.action_notification_settings),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                SettingToggleRow(
                    title = stringResource(R.string.settings_charge_alert_title, state.chargeAlertThreshold),
                    description = stringResource(R.string.settings_charge_alert_desc),
                    checked = state.chargeAlertEnabled,
                    onCheckedChange = onToggleChargeAlert,
                )
                if (state.chargeAlertEnabled) {
                    Slider(
                        value = state.chargeAlertThreshold.toFloat(),
                        onValueChange = { onPreviewChargeThreshold((round(it / 5f) * 5f).toInt()) },
                        onValueChangeFinished = onCommitChargeThreshold,
                        valueRange = 60f..100f,
                        steps = 7,
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                SettingToggleRow(
                    title = stringResource(
                        R.string.settings_temp_alert_title,
                        state.temperatureUnit.formatIntThreshold(context, state.tempAlertThreshold),
                    ),
                    description = stringResource(R.string.settings_temp_alert_desc),
                    checked = state.tempAlertEnabled,
                    onCheckedChange = onToggleTempAlert,
                )
                if (state.tempAlertEnabled) {
                    Slider(
                        value = state.tempAlertThreshold.toFloat(),
                        onValueChange = { onPreviewTempThreshold(round(it).toInt()) },
                        onValueChangeFinished = onCommitTempThreshold,
                        valueRange = 35f..50f,
                        steps = 14,
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                SettingToggleRow(
                    title = stringResource(R.string.settings_low_alert_title, state.lowAlertThreshold),
                    description = stringResource(R.string.settings_low_alert_desc),
                    checked = state.lowAlertEnabled,
                    onCheckedChange = onToggleLowAlert,
                )
                if (state.lowAlertEnabled) {
                    Slider(
                        value = state.lowAlertThreshold.toFloat(),
                        onValueChange = { onPreviewLowThreshold((round(it / 5f) * 5f).toInt()) },
                        onValueChangeFinished = onCommitLowThreshold,
                        valueRange = 5f..30f,
                        steps = 4,
                    )
                }
            }

            // 4. Appearance & Units
            Panel {
                SectionLabel(stringResource(R.string.settings_group_appearance).uppercase())
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_theme_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.themeMode == ThemeMode.SYSTEM,
                        onClick = { onSelectTheme(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.theme_system)) },
                    )
                    FilterChip(
                        selected = state.themeMode == ThemeMode.DARK,
                        onClick = { onSelectTheme(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.theme_dark)) },
                    )
                    FilterChip(
                        selected = state.themeMode == ThemeMode.LIGHT,
                        onClick = { onSelectTheme(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.theme_light)) },
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.settings_temp_unit_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.temperatureUnit == TemperatureUnit.CELSIUS,
                        onClick = { onSelectTemperatureUnit(TemperatureUnit.CELSIUS) },
                        label = { Text(stringResource(R.string.unit_celsius)) },
                    )
                    FilterChip(
                        selected = state.temperatureUnit == TemperatureUnit.FAHRENHEIT,
                        onClick = { onSelectTemperatureUnit(TemperatureUnit.FAHRENHEIT) },
                        label = { Text(stringResource(R.string.unit_fahrenheit)) },
                    )
                }
            }

            // 5. History & Data
            Panel {
                SectionLabel(stringResource(R.string.settings_group_history).uppercase())
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_retention_title, retentionDays),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_retention_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryStore.RETENTION_OPTIONS.forEach { days ->
                        FilterChip(
                            selected = days == retentionDays,
                            enabled = !state.retentionBusy,
                            onClick = { onSelectRetention(days) },
                            label = { Text(stringResource(R.string.format_days, days)) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onExportCsv,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_export_csv))
                    }
                    OutlinedButton(
                        onClick = onRequestClearHistory,
                        enabled = !state.retentionBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_clear_history))
                    }
                }
            }

            // 6. About
            Panel {
                SectionLabel(stringResource(R.string.settings_group_about).uppercase())
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${stringResource(R.string.app_name)} · ${stringResource(R.string.about_version, BuildConfig.VERSION_NAME)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_privacy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
