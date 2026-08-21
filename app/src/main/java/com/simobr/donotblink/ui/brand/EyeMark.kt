package com.simobr.donotblink.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simobr.donotblink.ui.theme.DnbColor

/**
 * The app mark, drawn from design/brand/do-not-blink.svg in its own 512-unit space.
 *
 * The SVG's blurred duplicates become wider, lower-alpha strokes of the same path: no blur, no
 * RenderEffect, no shadow anywhere in this app.
 *
 * The two animated values arrive as lambdas so they are read inside the draw lambda and invalidate
 * the draw phase only.
 */
@Composable
fun EyeMark(
    modifier: Modifier = Modifier,
    size: Dp = 236.dp,
    arcSweep: () -> Float = { 1f },
    bloomAlpha: () -> Float = { 1f },
) {
    Canvas(modifier.size(size)) {
        drawEyeMark(arcSweep = arcSweep(), bloomAlpha = bloomAlpha())
    }
}

/** Iris gradient stops and the lid fill, straight off the mark. Not palette tokens. */
private val IrisHighlight = Color(0xFFFFE49A)
private val IrisShadow = Color(0xFF8A5A00)
private val LidFill = Color(0xFF0A0700)

fun DrawScope.drawEyeMark(arcSweep: Float, bloomAlpha: Float) {
    val unit = kotlin.math.min(this.size.width, this.size.height) / 512f
    val centre = Offset(this.size.width / 2f, this.size.height / 2f)
    fun at(x: Float, y: Float) = Offset(centre.x + (x - 256f) * unit, centre.y + (y - 256f) * unit)
    fun len(v: Float) = v * unit

    // the bloom behind the mark
    if (bloomAlpha > 0f) {
        val bloom = len(220f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to DnbColor.Phosphor.copy(alpha = 0.14f * bloomAlpha),
                1f to Color.Transparent,
                center = centre,
                radius = bloom,
            ),
            radius = bloom,
            center = centre,
        )
    }

    // the dim ring the arc runs on
    drawCircle(DnbColor.Hairline, len(212f), centre, style = Stroke(len(7f)))

    // the countdown arc: soft pass, then the crisp one
    if (arcSweep > 0f) {
        val r = len(212f)
        val box = Size(r * 2, r * 2)
        val topLeft = Offset(centre.x - r, centre.y - r)
        val sweep = 360f * arcSweep.coerceIn(0f, 1f)
        drawArc(
            color = DnbColor.Phosphor.copy(alpha = 0.35f),
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = len(11f), cap = StrokeCap.Round),
        )
        drawArc(
            color = DnbColor.Hot,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = len(4f), cap = StrokeCap.Round),
        )
    }

    // the lid
    val lid = Path().apply {
        val start = at(92f, 256f)
        moveTo(start.x, start.y)
        val c1 = at(256f, 118f)
        val e1 = at(420f, 256f)
        quadraticTo(c1.x, c1.y, e1.x, e1.y)
        val c2 = at(256f, 394f)
        quadraticTo(c2.x, c2.y, start.x, start.y)
        close()
    }
    drawPath(lid, DnbColor.Phosphor.copy(alpha = 0.30f), style = Stroke(len(20f), join = StrokeJoin.Round))
    drawPath(lid, LidFill)
    drawPath(lid, DnbColor.Hot, style = Stroke(len(12f), join = StrokeJoin.Round))

    // the iris
    val irisRadius = len(66f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to IrisHighlight,
            0.55f to DnbColor.Phosphor,
            1f to IrisShadow,
            center = at(245.4f, 240.2f),
            radius = len(85.8f),
        ),
        radius = irisRadius,
        center = centre,
    )
    drawCircle(DnbColor.Phosphor.copy(alpha = 0.5f), irisRadius, centre, style = Stroke(len(6f)))

    // the pupil, its ring, and the catchlight
    drawCircle(DnbColor.Black, len(32f), centre)
    drawCircle(DnbColor.Phosphor, len(16f), centre, style = Stroke(len(4f)))
    drawCircle(DnbColor.Glint.copy(alpha = 0.95f), len(9f), at(234f, 232f))
}
