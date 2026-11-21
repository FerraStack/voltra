package dev.voltra.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.voltra.BatterySnapshot
import dev.voltra.R
import dev.voltra.ui.Formatters
import dev.voltra.ui.VoltraUiState
import dev.voltra.ui.components.ChartMetric
import dev.voltra.ui.components.DetailLine
import dev.voltra.ui.components.HistoryChart
import dev.voltra.ui.components.MetricCard
import dev.voltra.ui.components.NoteText
import dev.voltra.ui.components.Panel
import dev.voltra.ui.components.SectionLabel
import dev.voltra.ui.components.SectionTitle

@Composable
fun HistoryScreen(
    state: VoltraUiState,
    snapshot: BatterySnapshot,
    onSelectMetric: (ChartMetric) -> Unit,
    onSelectDays: (Int) -> Unit,
    onShowMoreSessions: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val observations = snapshot.observations
    val sessions = snapshot.sessions
    val estimate = snapshot.estimates
    val nowMs = snapshot.reading.time
    val dash = stringResource(R.string.placeholder_dash)

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
            Panel {
                SectionLabel(stringResource(state.chartMetric.titleRes()))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChartMetric.entries.forEach { metric ->
                        FilterChip(
                            selected = metric == state.chartMetric,
                            onClick = { onSelectMetric(metric) },
                            label = { Text(stringResource(metric.labelRes())) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.chartDays == 1,
                        onClick = { onSelectDays(1) },
                        label = { Text(stringResource(R.string.range_24h)) },
                    )
                    FilterChip(
                        selected = state.chartDays == 7,
                        onClick = { onSelectDays(7) },
                        label = { Text(stringResource(R.string.range_7d)) },
                    )
                    if (snapshot.retentionDays >= 30) {
                        FilterChip(
                            selected = state.chartDays == 30,
                            onClick = { onSelectDays(30) },
                            label = { Text(stringResource(R.string.range_30d)) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                val points = remember(observations, nowMs, state.chartDays) {
                    val start = nowMs - state.chartDays * 24 * 60 * 60_000L
                    observations.filter { it.time in start..nowMs }
                }
                val valid = remember(points, state.chartMetric, state.temperatureUnit) {
                    points.filter { state.chartMetric.value(it, state.temperatureUnit) != null }
                }

                if (valid.size < 2) {
                    Text(
                        text = stringResource(R.string.chart_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 35.dp),
                    )
                } else {
                    HistoryChart(
                        points = points,
                        days = state.chartDays,
                        nowMs = nowMs,
                        metric = state.chartMetric,
                        temperatureUnit = state.temperatureUnit,
                    )
                    Spacer(Modifier.height(10.dp))
                    val values = valid.mapNotNull { state.chartMetric.value(it, state.temperatureUnit) }
                    val (low, high) = state.chartMetric.bounds(values, state.temperatureUnit)
                    Text(
                        text = stringResource(
                            R.string.chart_scale_meta,
                            state.chartMetric.formatValue(context, low, state.temperatureUnit),
                            state.chartMetric.formatValue(context, high, state.temperatureUnit),
                            Formatters.dateTime(valid.last().time),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    if (state.chartMetric == ChartMetric.CURRENT) {
                        Text(
                            text = stringResource(R.string.chart_current_calibration_note),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            SectionTitle(
                title = stringResource(R.string.section_speed),
                subtitle = stringResource(R.string.section_speed_sub),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    title = stringResource(R.string.state_charging),
                    value = estimate.chargeSpeed?.let { Formatters.speed(context, it) } ?: dash,
                    hint = stringResource(R.string.metric_avg_hint),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = stringResource(R.string.state_discharging),
                    value = estimate.dischargeSpeed?.let { Formatters.speed(context, it) } ?: dash,
                    hint = stringResource(R.string.metric_avg_hint),
                    modifier = Modifier.weight(1f),
                )
            }

            if (estimate.observedScreenOn > 0 || estimate.observedScreenOff > 0) {
                SectionTitle(
                    title = stringResource(R.string.section_screen_24h),
                    subtitle = stringResource(R.string.section_screen_24h_sub),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricCard(
                        title = stringResource(R.string.screen_on_short),
                        value = Formatters.estimate(context, estimate.observedScreenOn),
                        hint = stringResource(R.string.metric_approximate),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        title = stringResource(R.string.screen_off_short),
                        value = Formatters.estimate(context, estimate.observedScreenOff),
                        hint = stringResource(R.string.metric_approximate),
                        modifier = Modifier.weight(1f),
                    )
                }
                NoteText(stringResource(R.string.note_observed_screen_24h))
            }

            if (estimate.sourceSpeeds.isNotEmpty()) {
                Panel {
                    Text(
                        text = stringResource(R.string.section_by_source),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    estimate.sourceSpeeds.forEach { (type, speed) ->
                        DetailLine(
                            label = Formatters.plugType(context, type),
                            value = Formatters.speed(context, speed),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.note_source_speeds),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }

            SectionTitle(
                title = stringResource(R.string.section_sessions),
                subtitle = stringResource(R.string.section_sessions_sub, snapshot.retentionDays),
            )
            val allSessions = remember(sessions) { sessions.asReversed().filter { it.sampleCount >= 2 } }
            val recent = allSessions.take(state.visibleSessions)
            if (recent.isEmpty()) {
                NoteText(stringResource(R.string.sessions_empty))
            }
            recent.forEach { session ->
                Panel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = Formatters.sessionType(context, session.charging, session.start.plugged),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${session.start.level}% → ${session.end.level}%",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.session_meta_line,
                            Formatters.dateTime(session.start.time),
                            Formatters.duration(context, session.end.time - session.start.time),
                            session.sampleCount,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    val deltaCounter = if (session.start.counter != null && session.end.counter != null) {
                        (session.end.counter - session.start.counter) / 1000
                    } else {
                        null
                    }
                    val details = buildList {
                        if (session.start.plugged) {
                            session.start.plugType?.let { add(Formatters.plugType(context, it)) }
                        }
                        deltaCounter?.let {
                            add(stringResource(R.string.session_counter_delta, if (it > 0) "+" else "", it))
                        }
                        session.peakTemperature?.let {
                            add(
                                stringResource(
                                    R.string.session_peak_temp,
                                    Formatters.temperature(context, it, state.temperatureUnit),
                                ),
                            )
                        }
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            text = details.joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    session.capacity?.let {
                        Text(
                            text = stringResource(R.string.session_capacity_est, it),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    session.capacityReason?.let { reason ->
                        Text(
                            text = stringResource(
                                R.string.session_capacity_excluded,
                                Formatters.exclusionReason(context, reason),
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            if (recent.size < allSessions.size) {
                OutlinedButton(
                    onClick = onShowMoreSessions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_show_more_sessions, allSessions.size - recent.size))
                }
            }

            OutlinedButton(
                onClick = onExportCsv,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_export_csv))
            }

            NoteText(stringResource(R.string.note_history_retention, snapshot.retentionDays))
            Spacer(Modifier.height(12.dp))
        }
    }
}
