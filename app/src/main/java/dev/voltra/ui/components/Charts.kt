package dev.voltra.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.voltra.Observation
import dev.voltra.R
import dev.voltra.Sessions
import dev.voltra.TemperatureUnit
import dev.voltra.ui.Formatters
import dev.voltra.ui.theme.LocalVoltraColors
import dev.voltra.ui.theme.VoltraExtendedColors
import java.util.Locale
import kotlin.math.abs

enum class ChartMetric {
    LEVEL,
    TEMPERATURE,
    CURRENT,
    VOLTAGE;

    fun titleRes(): Int = when (this) {
        LEVEL -> R.string.chart_title_level
        TEMPERATURE -> R.string.chart_title_temp
        CURRENT -> R.string.chart_title_current
        VOLTAGE -> R.string.chart_title_voltage
    }

    fun labelRes(): Int = when (this) {
        LEVEL -> R.string.chart_label_level
        TEMPERATURE -> R.string.chart_label_temp
        CURRENT -> R.string.chart_label_current
        VOLTAGE -> R.string.chart_label_voltage
    }

    fun color(extended: VoltraExtendedColors): Color = when (this) {
        LEVEL -> extended.chartLevel
        TEMPERATURE -> extended.chartTemperature
        CURRENT -> extended.chartCurrent
        VOLTAGE -> extended.chartVoltage
    }

    fun value(point: Observation, tempUnit: TemperatureUnit): Float? = when (this) {
        LEVEL -> point.level.toFloat()
        TEMPERATURE -> point.temperature?.let { tempUnit.convertFromCelsius(it) }
        CURRENT -> point.currentMicroamps?.div(1000f)
        VOLTAGE -> point.voltageMv?.div(1000f)
    }

    fun bounds(values: List<Float>, tempUnit: TemperatureUnit): Pair<Float, Float> {
        if (this == LEVEL) return 0f to 100f
        val min = values.min()
        val max = values.max()
        val minPad = when (this) {
            TEMPERATURE -> if (tempUnit == TemperatureUnit.FAHRENHEIT) 3.6f else 2f
            CURRENT -> 50f
            VOLTAGE -> 0.1f
            LEVEL -> 1f
        }
        val padding = maxOf((max - min) * 0.1f, minPad)
        return (min - padding) to (max + padding)
    }

    fun formatValue(context: Context, value: Float, tempUnit: TemperatureUnit): String = when (this) {
        LEVEL -> String.format(Locale.getDefault(), "%.0f%%", value)
        TEMPERATURE -> {
            val resId = if (tempUnit == TemperatureUnit.CELSIUS) R.string.format_temp_c else R.string.format_temp_f
            String.format(Locale.getDefault(), context.getString(resId), value)
        }
        CURRENT -> String.format(Locale.getDefault(), "%.0f %s", value, context.getString(R.string.unit_ma))
        VOLTAGE -> String.format(Locale.getDefault(), context.getString(R.string.format_voltage), value)
    }

    fun formatAxis(value: Float): String = when (this) {
        LEVEL -> String.format(Locale.getDefault(), "%.0f%%", value)
        TEMPERATURE -> String.format(Locale.getDefault(), "%.0f°", value)
        CURRENT -> String.format(Locale.getDefault(), "%.0f", value)
        VOLTAGE -> String.format(Locale.getDefault(), "%.2f", value)
    }
}

@Composable
fun HistoryChart(
    points: List<Observation>,
    days: Int,
    nowMs: Long,
    metric: ChartMetric,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val extended = LocalVoltraColors.current
    val lineColor = metric.color(extended)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val validPoints = remember(points, metric, temperatureUnit) {
        points.mapNotNull { item -> metric.value(item, temperatureUnit)?.let { item to it } }
    }
    if (validPoints.size < 2) return

    val values = remember(validPoints) { validPoints.map { it.second } }
    val (low, high) = remember(values, metric, temperatureUnit) { metric.bounds(values, temperatureUnit) }
    val mid = (low + high) / 2f
    val span = days * 24 * 60 * 60_000L
    val earliest = nowMs - span

    var scrubbedPoint by remember(points, days, metric, temperatureUnit) {
        mutableStateOf<Pair<Observation, Float>?>(null)
    }

    fun findNearest(touchX: Float, width: Float): Pair<Observation, Float>? {
        if (width <= 0f || validPoints.isEmpty()) return null
        val fraction = (touchX / width).coerceIn(0f, 1f)
        val targetTime = earliest + (span * fraction).toLong()
        return validPoints.minByOrNull { abs(it.first.time - targetTime) }
    }

    val chartDescription = stringResource(
        R.string.cd_history_chart,
        stringResource(metric.labelRes()),
        days,
        metric.formatValue(context, low, temperatureUnit),
        metric.formatValue(context, high, temperatureUnit),
        metric.formatValue(context, values.last(), temperatureUnit),
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Scrubbing readout bar
        val active = scrubbedPoint
        if (active != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Formatters.dateTime(active.first.time),
                    color = muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = metric.formatValue(context, active.second, temperatureUnit),
                    color = lineColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Text(
                text = stringResource(R.string.chart_scrub_hint),
                color = muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        // Y-axis labels + plot canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(metric.formatAxis(high), color = muted, fontSize = 10.sp, maxLines = 1)
                Text(metric.formatAxis(mid), color = muted, fontSize = 10.sp, maxLines = 1)
                Text(metric.formatAxis(low), color = muted, fontSize = 10.sp, maxLines = 1)
            }

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = chartDescription }
                    .pointerInput(validPoints, earliest, span) {
                        detectTapGestures { offset ->
                            val hit = findNearest(offset.x, size.width.toFloat())
                            scrubbedPoint = if (scrubbedPoint?.first?.time == hit?.first?.time) null else hit
                        }
                    }
                    .pointerInput(validPoints, earliest, span) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                scrubbedPoint = findNearest(offset.x, size.width.toFloat())
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                scrubbedPoint = findNearest(change.position.x, size.width.toFloat())
                            },
                        )
                    },
            ) {
                val range = (high - low).coerceAtLeast(0.001f)
                fun toOffset(item: Observation, value: Float) = Offset(
                    x = ((item.time - earliest).toFloat() / span).coerceIn(0f, 1f) * size.width,
                    y = size.height * (1f - ((value - low) / range).coerceIn(0f, 1f)),
                )

                for (fraction in listOf(0f, 0.5f, 1f)) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, size.height * fraction),
                        end = Offset(size.width, size.height * fraction),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                points.zipWithNext().forEach { (a, b) ->
                    val av = metric.value(a, temperatureUnit)
                    val bv = metric.value(b, temperatureUnit)
                    if (av != null && bv != null && b.time - a.time in 1..Sessions.MAX_GAP) {
                        drawLine(
                            color = lineColor,
                            start = toOffset(a, av),
                            end = toOffset(b, bv),
                            strokeWidth = 2.75.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }

                points.forEachIndexed { index, item ->
                    val value = metric.value(item, temperatureUnit) ?: return@forEachIndexed
                    val previous = points.getOrNull(index - 1)
                    val next = points.getOrNull(index + 1)
                    val joinedBefore = previous != null &&
                        metric.value(previous, temperatureUnit) != null &&
                        item.time - previous.time in 1..Sessions.MAX_GAP
                    val joinedAfter = next != null &&
                        metric.value(next, temperatureUnit) != null &&
                        next.time - item.time in 1..Sessions.MAX_GAP
                    if (points.size <= 200 || (!joinedBefore && !joinedAfter)) {
                        drawCircle(lineColor, 2.dp.toPx(), toOffset(item, value))
                    }
                }

                scrubbedPoint?.let { (obs, value) ->
                    val pt = toOffset(obs, value)
                    drawLine(
                        color = onSurface.copy(alpha = 0.45f),
                        start = Offset(pt.x, 0f),
                        end = Offset(pt.x, size.height),
                        strokeWidth = 1.25.dp.toPx(),
                    )
                    drawCircle(onSurface, 5.5.dp.toPx(), pt)
                    drawCircle(lineColor, 3.5.dp.toPx(), pt)
                }
            }
        }

        // X-axis time labels
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(Formatters.axisTime(earliest, days), color = muted, fontSize = 10.sp)
            Text(Formatters.axisTime(earliest + span / 2L, days), color = muted, fontSize = 10.sp)
            Text(Formatters.axisTime(nowMs, days), color = muted, fontSize = 10.sp)
        }
    }
}

@Composable
fun CapacityChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.cd_capacity_chart, values.size, values.last())

    val low = (values.min() * 0.9f)
    val high = (values.max() * 1.1f).coerceAtLeast(low + 1f)
    val mid = (low + high) / 2f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text("${high.toInt()}", color = muted, fontSize = 10.sp, maxLines = 1)
            Text("${mid.toInt()}", color = muted, fontSize = 10.sp, maxLines = 1)
            Text("${low.toInt()}", color = muted, fontSize = 10.sp, maxLines = 1)
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics { contentDescription = description },
        ) {
            val points = values.mapIndexed { index, value ->
                Offset(
                    x = size.width * index / (values.size - 1),
                    y = size.height * (1f - (value - low) / (high - low)),
                )
            }
            for (fraction in listOf(0f, 0.5f, 1f)) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height * fraction),
                    end = Offset(size.width, size.height * fraction),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            points.zipWithNext().forEach { (a, b) ->
                drawLine(primary, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            points.forEach { drawCircle(primary, 3.dp.toPx(), it) }
        }
    }
}
