package com.ojas.assistant.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ojas.assistant.ui.theme.SpaceOutline
import com.ojas.assistant.ui.theme.StarlightFaint

/**
 * A ring gauge drawn as an orbit: a faint full track for the whole goal, a bright arc
 * for what has been achieved, and a small body at the leading edge.
 */
@Composable
fun OrbitRing(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    trackColor: Color = SpaceOutline,
    content: @Composable () -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 620),
        label = "orbit-progress"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = strokeWidth.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to accent.copy(alpha = 0.35f),
                        0.5f to accent,
                        1f to accent.copy(alpha = 0.35f)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                // The leading body, so the ring reads as something in motion.
                val angle = Math.toRadians((-90f + 360f * animated).toDouble())
                val radius = diameter / 2f
                val centre = Offset(size.width / 2f, size.height / 2f)
                val head = Offset(
                    centre.x + (radius * kotlin.math.cos(angle)).toFloat(),
                    centre.y + (radius * kotlin.math.sin(angle)).toFloat()
                )
                drawCircle(color = accent.copy(alpha = 0.22f), radius = stroke * 1.15f, center = head)
                drawCircle(color = Color.White, radius = stroke * 0.30f, center = head)
            }
        }
        content()
    }
}

/** Seven-day bar strip. Values are already normalised by the caller. */
@Composable
fun SparkBars(
    values: List<Float>,
    labels: List<String>,
    accent: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 62.dp
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEach { raw ->
                val value = raw.coerceIn(0f, 1f)
                val animated by animateFloatAsState(
                    targetValue = value,
                    animationSpec = tween(520),
                    label = "spark-bar"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                        val radius = size.width / 2f
                        val filled = (size.height * animated).coerceAtLeast(if (animated > 0f) radius * 1.2f else radius * 0.5f)

                        drawRoundRect(
                            color = SpaceOutline.copy(alpha = 0.55f),
                            topLeft = Offset(0f, size.height - radius),
                            size = Size(size.width, radius),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                        )
                        if (animated > 0f) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    listOf(accent, accent.copy(alpha = 0.42f))
                                ),
                                topLeft = Offset(0f, size.height - filled),
                                size = Size(size.width, filled),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = StarlightFaint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** A thin horizontal meter, used for per-app screen time rows. */
@Composable
fun LinearMeter(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1.4f),
        animationSpec = tween(480),
        label = "meter"
    )
    Canvas(modifier.fillMaxWidth().height(height)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = SpaceOutline.copy(alpha = 0.7f),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
        )
        val width = (size.width * animated.coerceAtMost(1f))
        if (width > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.65f), accent)),
                size = Size(width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
            )
        }
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(5.dp)) { drawCircle(accent) }
            Spacer(Modifier.width(9.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = StarlightFaint)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
