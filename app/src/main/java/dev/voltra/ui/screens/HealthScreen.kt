package dev.voltra.ui.screens

import android.os.Build
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.voltra.ui.Formatters
import dev.voltra.ui.VoltraUiState
import dev.voltra.ui.components.CapacityChart
import dev.voltra.ui.components.DetailLine
import dev.voltra.ui.components.MetricCard
import dev.voltra.ui.components.NoteText
import dev.voltra.ui.components.Panel
import dev.voltra.ui.components.SectionLabel
import dev.voltra.ui.components.SectionTitle
import dev.voltra.ui.theme.LocalVoltraColors

@Composable
fun HealthScreen(
    state: VoltraUiState,
    snapshot: BatterySnapshot,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val extended = LocalVoltraColors.current
    val reading = snapshot.reading
    val estimate = snapshot.estimates
    val sessions = snapshot.sessions
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
                SectionLabel(stringResource(R.string.label_capacity_estimate))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = estimate.healthPercent?.let { stringResource(R.string.format_percent, it) } ?: dash,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.health_of_design_capacity),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.health_summary_detail, sessions.count { it.capacity != null }),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    fontSize = 14.sp,
                )
                if ((estimate.healthPercent ?: 0) > 100) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.health_over_100_warning),
                        color = extended.warning,
                        lineHeight = 20.sp,
                        fontSize = 13.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    title = stringResource(R.string.metric_cycles),
                    value = reading.cycles?.toString() ?: dash,
                    hint = stringResource(R.string.metric_condition_hint),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = stringResource(R.string.metric_wear),
                    value = estimate.healthPercent?.takeIf { it in 0..100 }
                        ?.let { stringResource(R.string.format_percent, 100 - it) } ?: dash,
                    hint = stringResource(R.string.metric_approximate),
                    modifier = Modifier.weight(1f),
                )
            }

            val counterText = reading.counter?.let { stringResource(R.string.format_mah, it / 1000) }
                ?: stringResource(R.string.charge_counter_unsupported)
            Text(
                text = stringResource(R.string.charge_counter_line, counterText),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            SectionTitle(
                title = stringResource(R.string.section_details),
                subtitle = stringResource(R.string.section_estimate),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    title = stringResource(R.string.metric_measured),
                    value = estimate.capacityMah?.let { stringResource(R.string.format_mah, it) } ?: dash,
                    hint = stringResource(R.string.metric_capacity_hint),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = stringResource(R.string.metric_design),
                    value = state.designCapacity.takeIf { it > 0 }?.let { stringResource(R.string.format_mah, it) } ?: dash,
                    hint = stringResource(R.string.metric_from_settings),
                    modifier = Modifier.weight(1f),
                )
            }

            Panel {
                SectionLabel(stringResource(R.string.label_capacity_history))
                val capacities = sessions.mapNotNull { it.capacity }.takeLast(10)
                Spacer(Modifier.height(12.dp))
                if (capacities.size < 2) {
                    Text(
                        text = stringResource(R.string.capacity_chart_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                } else {
                    CapacityChart(capacities)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.capacity_chart_range,
                            capacities.min(),
                            capacities.max(),
                            capacities.size,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }

            Panel {
                Text(
                    text = stringResource(R.string.health_howto_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.health_howto_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.action_set_design_capacity))
                }
            }

            SectionTitle(
                title = stringResource(R.string.section_device),
                subtitle = stringResource(R.string.section_android),
            )
            Panel {
                DetailLine(stringResource(R.string.detail_model), "${Build.MANUFACTURER} ${Build.MODEL}")
                DetailLine(stringResource(R.string.detail_android_version), Build.VERSION.RELEASE ?: dash)
                DetailLine(
                    stringResource(R.string.detail_chemistry),
                    reading.technology ?: stringResource(R.string.health_unknown),
                )
                DetailLine(
                    stringResource(R.string.detail_system_status),
                    Formatters.healthStatus(context, reading.status),
                )
            }

            NoteText(stringResource(R.string.note_health_caveat))
            Spacer(Modifier.height(12.dp))
        }
    }
}
