package app.pocketfree.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Chart geometry uses Float for drawing only. Money values are never computed with floating point.

data class DonutSlice(val value: Long, val color: Color)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 22.dp,
    center: @Composable () -> Unit = {}
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val total = slices.sumOf { it.value }.toFloat()
            val stroke = Stroke(width = strokeWidth.toPx())
            if (total <= 0f) {
                drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            } else {
                var start = -90f
                for (slice in slices) {
                    val sweep = 360f * (slice.value.toFloat() / total)
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = if (slices.size > 1) (sweep - 1.5f).coerceAtLeast(0.1f) else sweep,
                        useCenter = false,
                        style = stroke
                    )
                    start += sweep
                }
            }
        }
        center()
    }
}

@Composable
fun LineChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    val fill = lineColor.copy(alpha = 0.14f)
    val baseline = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier) {
        drawLine(color = baseline, start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height - (v.toFloat() / max) * size.height * 0.88f - size.height * 0.04f)
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (p in points.drop(1)) lineTo(p.x, p.y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

data class BarGroup(val label: String, val first: Long, val second: Long)

@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    firstColor: Color,
    secondColor: Color,
    modifier: Modifier = Modifier
) {
    val baseline = MaterialTheme.colorScheme.outline
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            drawLine(color = baseline, start = Offset(0f, size.height), end = Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            if (groups.isEmpty()) return@Canvas
            val max = groups.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1L).toFloat()
            val groupWidth = size.width / groups.size
            val barWidth = minOf(groupWidth * 0.3f, 26.dp.toPx())
            val corner = CornerRadius(5.dp.toPx())
            groups.forEachIndexed { i, g ->
                val centerX = groupWidth * i + groupWidth / 2f
                val h1 = (g.first.toFloat() / max) * (size.height - 4.dp.toPx())
                val h2 = (g.second.toFloat() / max) * (size.height - 4.dp.toPx())
                drawRoundRect(firstColor, Offset(centerX - barWidth - 1.dp.toPx(), size.height - h1), Size(barWidth, h1), corner)
                drawRoundRect(secondColor, Offset(centerX + 1.dp.toPx(), size.height - h2), Size(barWidth, h2), corner)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            groups.forEach {
                Text(
                    text = it.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
