package com.snakechase.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.snakechase.app.domain.model.Vec2
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision Virtual Gamepad Thumbstick.
 * Replicates the authentic console-grade directional thumbstick design with:
 * - Dark slate circular base chassis with 4 quadrant sector divider lines
 * - 4 directional arrow indicators (▲, ▼, ◄, ►) that illuminate with Cyan glow when active
 * - Recessed concentric inner well with radial depth
 * - Stalk/neck column tiers
 * - Elevated 3D metallic thumb cap knob with spring-back physics
 */
@Composable
fun VirtualJoystick(
    onVectorChange: (Vec2) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
) {
    val coroutineScope = rememberCoroutineScope()
    var knobOffsetX by remember { mutableStateOf(0f) }
    var knobOffsetY by remember { mutableStateOf(0f) }
    val animKnobX = remember { Animatable(0f) }
    val animKnobY = remember { Animatable(0f) }

    val baseRadiusPx = remember { mutableStateOf(0f) }
    val maxTravelRadiusPx = remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val maxRadius = this.size.width * 0.34f
                baseRadiusPx.value = this.size.width / 2f
                maxTravelRadiusPx.value = maxRadius

                detectDragGestures(
                    onDragStart = { offset ->
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val dist = sqrt(dx * dx + dy * dy)
                        val clampedDist = minOf(dist, maxRadius)
                        val angle = atan2(dy, dx)
                        val targetX = cos(angle) * clampedDist
                        val targetY = sin(angle) * clampedDist

                        knobOffsetX = targetX
                        knobOffsetY = targetY
                        coroutineScope.launch {
                            animKnobX.snapTo(targetX)
                            animKnobY.snapTo(targetY)
                        }

                        val normX = if (maxRadius > 0) (targetX / maxRadius).coerceIn(-1f, 1f) else 0f
                        val normY = if (maxRadius > 0) (targetY / maxRadius).coerceIn(-1f, 1f) else 0f
                        onVectorChange(Vec2(normX, normY))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val dx = change.position.x - center.x
                        val dy = change.position.y - center.y
                        val dist = sqrt(dx * dx + dy * dy)
                        val clampedDist = minOf(dist, maxRadius)
                        val angle = atan2(dy, dx)
                        val targetX = cos(angle) * clampedDist
                        val targetY = sin(angle) * clampedDist

                        knobOffsetX = targetX
                        knobOffsetY = targetY
                        coroutineScope.launch {
                            animKnobX.snapTo(targetX)
                            animKnobY.snapTo(targetY)
                        }

                        val normX = if (maxRadius > 0) (targetX / maxRadius).coerceIn(-1f, 1f) else 0f
                        val normY = if (maxRadius > 0) (targetY / maxRadius).coerceIn(-1f, 1f) else 0f
                        onVectorChange(Vec2(normX, normY))
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            animKnobX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                        }
                        coroutineScope.launch {
                            animKnobY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                        }
                        knobOffsetX = 0f
                        knobOffsetY = 0f
                        onVectorChange(Vec2.ZERO)
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            animKnobX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                        }
                        coroutineScope.launch {
                            animKnobY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                        }
                        knobOffsetX = 0f
                        knobOffsetY = 0f
                        onVectorChange(Vec2.ZERO)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val currentKnobX = animKnobX.value
        val currentKnobY = animKnobY.value

        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = this.size.width / 2f - 2f
            val wellRadius = outerRadius * 0.62f
            val knobRadius = outerRadius * 0.38f

            val currentDist = sqrt(currentKnobX * currentKnobX + currentKnobY * currentKnobY)
            val currentAngleDeg = (atan2(currentKnobY.toDouble(), currentKnobX.toDouble()) * 180.0 / kotlin.math.PI).toFloat()

            // Sticker-style stick: jungle base on a hard ink shadow, cream arrows, banana knob.
            val ink = Color(0xFF0E1F17)
            drawCircle(ink, outerRadius, center + Offset(0f, 5f))
            drawCircle(Color(0xFF1E3B2F), outerRadius - 2f, center)
            drawCircle(ink, outerRadius - 2f, center, style = Stroke(width = 4f))

            val arrowDist = (outerRadius + wellRadius) / 2f
            val activeThreshold = outerRadius * 0.12f
            val pushed = currentDist > activeThreshold
            drawDirectionalTriangle(center + Offset(0f, -arrowDist), -90f, 11f, pushed && currentAngleDeg in -135f..-45f)
            drawDirectionalTriangle(center + Offset(0f, arrowDist), 90f, 11f, pushed && currentAngleDeg in 45f..135f)
            drawDirectionalTriangle(center + Offset(-arrowDist, 0f), 180f, 11f, pushed && (currentAngleDeg > 135f || currentAngleDeg < -135f))
            drawDirectionalTriangle(center + Offset(arrowDist, 0f), 0f, 11f, pushed && currentAngleDeg in -45f..45f)

            // Well
            drawCircle(ink.copy(alpha = 0.45f), wellRadius, center)
            drawCircle(ink, wellRadius, center, style = Stroke(width = 3f))

            // Knob on its own hard shadow; turns lime while pushed.
            val knobCenter = center + Offset(currentKnobX, currentKnobY)
            drawCircle(ink, knobRadius + 2f, knobCenter + Offset(0f, 5f))
            drawCircle(if (pushed) Color(0xFFC8F560) else Color(0xFFFFD23F), knobRadius, knobCenter)
            drawCircle(ink, knobRadius, knobCenter, style = Stroke(width = 4f))
            drawCircle(Color(0xFFFFF4DC).copy(alpha = 0.8f), knobRadius * 0.28f, knobCenter - Offset(knobRadius * 0.35f, knobRadius * 0.35f))
        }
    }
}

/**
 * Draws a directional arrow: cream at rest, lime while the stick points that way.
 */
private fun DrawScope.drawDirectionalTriangle(
    center: Offset,
    directionAngle: Float,
    size: Float,
    isActive: Boolean,
) {
    val rad = (directionAngle.toDouble() * kotlin.math.PI / 180.0)
    val tip = center + Offset((cos(rad) * size * 1.1f).toFloat(), (sin(rad) * size * 1.1f).toFloat())
    val baseLeft = center + Offset((cos(rad + 2.4) * size * 0.9f).toFloat(), (sin(rad + 2.4) * size * 0.9f).toFloat())
    val baseRight = center + Offset((cos(rad - 2.4) * size * 0.9f).toFloat(), (sin(rad - 2.4) * size * 0.9f).toFloat())

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseLeft.x, baseLeft.y)
        lineTo(baseRight.x, baseRight.y)
        close()
    }
    drawPath(path = path, color = Color(0xFF0E1F17), style = Stroke(width = 4f))
    drawPath(path = path, color = if (isActive) Color(0xFFC8F560) else Color(0xFFFFF4DC), style = Fill)
}
