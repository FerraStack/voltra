package dev.voltra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.voltra.BatterySnapshot
import dev.voltra.R
import dev.voltra.Sessions
import dev.voltra.ui.Formatters
import dev.voltra.ui.VoltraUiState
import dev.voltra.ui.components.ChargeGauge
import dev.voltra.ui.components.MetricCard
import dev.voltra.ui.components.MetricGrid
import dev.voltra.ui.components.MetricItem
import dev.voltra.ui.components.NoteText
import dev.voltra.ui.components.Panel
import dev.voltra.ui.components.SectionLabel
import dev.voltra.ui.components.SectionTitle
import dev.voltra.ui.theme.LocalVoltraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    state: VoltraUiState,
    snapshot: BatterySnapshot,
    metricColumns: Int,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val extended = LocalVoltraColors.current
    val reading = snapshot.reading
    val estimate = snapshot.estimates
    val observations = snapshot.observations
    val sessions = snapshot.sessions
    val dash = stringResource(R.string.placeholder_dash)
    val stateLabel = Formatters.chargeState(context, reading)
    val sourceLabel = Formatters.powerSource(context, reading.source)

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
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
                if (state.designCapacity == 0 && !state.backgroundEnabled && !state.setupDismissed) {
                    Panel {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.setup_card_title),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(onClick = onDismissSetup) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_dismiss),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.setup_card_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onOpenSettings) {
                            Text(stringResource(R.string.setup_card_action))
                        }
                    }
                }

                // Live gauge hero panel
                Panel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel(stringResource(R.string.label_right_now))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stateLabel,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = sourceLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                        Text(
                            text = stringResource(R.string.live_badge),
                            color = extended.badgeContent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(extended.badgeContainer, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }

                    ChargeGauge(
                        level = reading.level,
                        stateDescription = "$stateLabel, $sourceLabel",
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                    SectionLabel(
                        stringResource(
                            if (reading.plugged) R.string.label_until_full else R.string.label_estimated_remaining,
                        ),
                    )
                    Spacer(Modifier.height(5.dp))
                    val forecastMinutes = when {
                        reading.full && reading.plugged -> 0
                        reading.plugged -> reading.chargeMinutes ?: estimate.chargeRemaining
                        else -> estimate.remainingMinutes
                    }
                    Text(
                        text = forecastMinutes?.let { Formatters.estimate(context, it) }
                            ?: stringResource(R.string.estimate_not_enough_data),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val sourceNoteRes = when {
                        !reading.plugged -> R.string.estimate_source_discharge
                        reading.chargeMinutes != null || reading.full -> R.string.estimate_source_android
                        estimate.chargeRemaining != null -> R.string.estimate_source_linear
                        else -> R.string.estimate_source_android_unavailable
                    }
                    Text(
                        text = stringResource(sourceNoteRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }

                // Device readings grid (2 columns compact, 4 columns wide)
                SectionTitle(
                    title = stringResource(R.string.section_metrics),
                    subtitle = stringResource(R.string.section_metrics_sub),
                )
                MetricGrid(
                    columns = metricColumns,
                    items = listOf(
                        MetricItem(
                            title = stringResource(R.string.metric_temperature),
                            value = reading.temperature?.let { Formatters.temperature(context, it, state.temperatureUnit) } ?: dash,
                            hint = stringResource(R.string.metric_temperature_hint),
                        ),
                        MetricItem(
                            title = stringResource(R.string.metric_voltage),
                            value = reading.voltage?.let { Formatters.voltage(context, it) } ?: dash,
                            hint = stringResource(R.string.metric_voltage_hint),
                        ),
                        MetricItem(
                            title = stringResource(R.string.metric_current),
                            value = reading.current?.let { Formatters.current(context, it) } ?: dash,
                            hint = stringResource(R.string.metric_current_hint),
                        ),
                        MetricItem(
                            title = stringResource(R.string.metric_condition),
                            value = Formatters.healthStatus(context, reading.status),
                            hint = stringResource(R.string.metric_condition_hint),
                        ),
                        MetricItem(
                            title = stringResource(R.string.metric_power),
                            value = if (reading.current != null && reading.voltage != null) {
                                Formatters.powerWatts(context, reading.current, reading.voltage)
                            } else {
                                dash
                            },
                            hint = stringResource(R.string.metric_power_hint),
                        ),
                        MetricItem(
                            title = stringResource(R.string.metric_source),
                            value = sourceLabel,
                            hint = stringResource(R.string.metric_source_hint),
                        ),
                        MetricItem(
                            title = stringResource(R.string.metric_avg_current),
                            value = reading.averageCurrent?.let { Formatters.current(context, it) } ?: dash,
                            hint = stringResource(R.string.metric_avg_current_hint),
                        ),
                        MetricItem(
                            title = stringResource(R.string.metric_energy),
                            value = reading.energyNwh?.let { Formatters.energyWh(context, it) } ?: dash,
                            hint = stringResource(R.string.metric_energy_hint),
                        ),
                    ),
                )
                NoteText(stringResource(R.string.note_power_and_forecast))

                // Current session summary
                val currentSession = sessions.lastOrNull()?.takeIf {
                    it.start.plugged == reading.plugged &&
                        it.charging == reading.charging &&
                        reading.time - it.end.time in 0..Sessions.MAX_GAP
                }
                if (currentSession != null) {
                    SectionTitle(
                        title = stringResource(R.string.section_current_session),
                        subtitle = Formatters.sessionType(context, reading.charging, reading.plugged),
                    )
                    Panel {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                SectionLabel(stringResource(R.string.label_since_observed))
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = "${currentSession.start.level}% → ${reading.level}%",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = Formatters.duration(context, reading.time - currentSession.start.time),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.session_started_meta,
                                Formatters.dateTime(currentSession.start.time),
                                currentSession.sampleCount,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }

                // Collection status card
                Panel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel(stringResource(R.string.label_data_collection))
                            Spacer(Modifier.height(5.dp))
                            val modeRes = when {
                                state.monitorRunning -> R.string.collection_continuous
                                state.backgroundEnabled && state.backgroundScheduled -> R.string.collection_background
                                else -> R.string.collection_foreground_only
                            }
                            Text(
                                text = stringResource(modeRes),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = observations.lastOrNull()?.let {
                                    stringResource(R.string.collection_last_sample, Formatters.dateTime(it.time))
                                } ?: stringResource(R.string.collection_no_samples),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        TextButton(onClick = onOpenSettings) {
                            Text(stringResource(R.string.action_configure))
                        }
                    }
                }

                // Screen-state forecast
                SectionTitle(
                    title = stringResource(R.string.section_screen_forecast),
                    subtitle = stringResource(R.string.section_approx),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        title = stringResource(R.string.metric_screen_on),
                        value = estimate.screenOnMinutes?.let { Formatters.estimate(context, it) } ?: dash,
                        hint = stringResource(R.string.metric_from_samples),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        title = stringResource(R.string.metric_screen_off),
                        value = estimate.screenOffMinutes?.let { Formatters.estimate(context, it) } ?: dash,
                        hint = stringResource(R.string.metric_from_samples),
                        modifier = Modifier.weight(1f),
                    )
                }
                NoteText(stringResource(R.string.note_screen_interactivity))
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
