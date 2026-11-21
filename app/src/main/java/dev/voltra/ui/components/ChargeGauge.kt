package dev.voltra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.voltra.R
import dev.voltra.ui.theme.LocalVoltraColors

@Composable
fun ChargeGauge(
    level: Int,
    stateDescription: String,
    modifier: Modifier = Modifier,
) {
    val dash = stringResource(R.string.placeholder_dash)
    val levelText = if (level >= 0) "$level%" else dash
    val gaugeDescription = stringResource(R.string.cd_charge_gauge, levelText, stateDescription)
    val trackColor = LocalVoltraColors.current.gaugeTrack
    val progressColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(205.dp)
            .clearAndSetSemantics { contentDescription = gaugeDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(182.dp)) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (level >= 0) {
                drawArc(
                    color = progressColor,
                    startAngle = 135f,
                    sweepAngle = 270f * level.coerceIn(0, 100) / 100f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = levelText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp,
            )
            Text(
                text = stringResource(R.string.label_charge),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
            )
        }
    }
}
