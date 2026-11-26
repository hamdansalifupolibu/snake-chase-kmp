package com.snakechase.app.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.snakechase.app.domain.model.Obstacle
import com.snakechase.app.domain.model.ObstacleTheme
import com.snakechase.app.domain.model.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object ObstacleRenderer {

    fun drawObstacles(drawScope: DrawScope, obstacles: List<Obstacle>, levelId: Int = 1) {
        val world = WorldStyle.forLevel(levelId)
        with(drawScope) {
            for (obs in obstacles) {
                if (!obs.isActive) continue

                when (obs) {
                    is Obstacle.Wall -> when (world) {
                        WorldStyle.GROVE -> drawLogWall(obs.rect)
                        WorldStyle.TUNDRA -> drawIceSlab(obs.rect)
                        else -> drawWall(obs)
                    }
                    is Obstacle.RotatingBar -> if (world == WorldStyle.NEON) drawRotatingBar(obs) else drawWorldBar(world, obs)
                    is Obstacle.MovingBlock -> when (world) {
                        WorldStyle.GROVE, WorldStyle.TUNDRA -> drawWorldBlock(world, obs)
                        else -> drawMovingBlock(obs)
                    }
                    is Obstacle.DangerZone -> if (world == WorldStyle.TUNDRA) drawThinIce(obs) else drawDangerZone(obs)
                    is Obstacle.Teleporter -> {
                        drawTeleporter(obs)
                        drawPortalTrim(world, obs)
                    }
                    is Obstacle.Shark -> drawShark(obs)
                    is Obstacle.ForestProwler -> drawForestProwler(obs)
                    is Obstacle.Isopod -> if (world == WorldStyle.TUNDRA) drawFrostling(obs) else drawIsopod(obs)
                    is Obstacle.Cyclone -> if (world == WorldStyle.TUNDRA) drawSnowWhirl(obs) else drawCyclone(obs)
                    is Obstacle.Jellyfish -> drawJellyfish(obs)
                    is Obstacle.MagmaCrack -> drawMagmaCrack(obs)
                    is Obstacle.FallingEmber -> if (world == WorldStyle.TUNDRA) drawIcicleDrop(obs) else drawFallingEmber(obs)
                    is Obstacle.EscapeDoor -> drawEscapeDoor(obs)
                }
            }
        }
    }

    private fun DrawScope.drawWall(wall: Obstacle.Wall) {
        val rect = wall.rect
        val (accentColor, bodyColor) = getThemeColors(wall.theme)
        val cornerRadius = CornerRadius(6f, 6f)

        // 1. Outer Soft Glow Halo
        drawRoundRect(
            color = accentColor.copy(alpha = 0.08f),
            topLeft = Offset(rect.left - 6f, rect.top - 6f),
            size = Size(rect.width + 12f, rect.height + 12f),
            cornerRadius = CornerRadius(10f, 10f),
        )
        drawRoundRect(
            color = accentColor.copy(alpha = 0.14f),
            topLeft = Offset(rect.left - 3f, rect.top - 3f),
            size = Size(rect.width + 6f, rect.height + 6f),
            cornerRadius = CornerRadius(8f, 8f),
        )

        // 2. Body Glass Plate Base
        drawRoundRect(
            color = bodyColor.copy(alpha = 0.88f),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = cornerRadius,
        )

        // 3. Top Accent Glass Tone
        val isHorizontal = rect.width >= rect.height
        if (isHorizontal) {
            drawRoundRect(
                color = accentColor.copy(alpha = 0.45f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height * 0.5f),
                cornerRadius = cornerRadius,
            )
            // Top White Specular Curve
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(rect.left + 6f, rect.top + 1.5f),
                end = Offset(rect.right - 6f, rect.top + 1.5f),
                strokeWidth = 1.2f,
            )
        } else {
            drawRoundRect(
                color = accentColor.copy(alpha = 0.45f),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width * 0.5f, rect.height),
                cornerRadius = cornerRadius,
            )
            // Left White Specular Curve
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(rect.left + 1.5f, rect.top + 6f),
                end = Offset(rect.left + 1.5f, rect.bottom - 6f),
                strokeWidth = 1.2f,
            )
        }

        // 4. Perimeter Neon Stroke
        drawRoundRect(
            color = accentColor.copy(alpha = 0.95f),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.5f),
        )

        // 5. Luminous Traversing Scanline Bead
        if (isHorizontal) {
            val scanX = rect.left + 8f + (rect.width - 16f) * wall.scanlinePhase
            val scanCenter = Offset(scanX, rect.centerY)
            val beadRadius = min(rect.height * 0.42f, 7f)

            // Scan halo
            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = beadRadius * 2.2f,
                center = scanCenter,
            )
            // Scan core
            drawCircle(
                color = Color.White,
                radius = beadRadius,
                center = scanCenter,
            )
        } else {
            val scanY = rect.top + 8f + (rect.height - 16f) * wall.scanlinePhase
            val scanCenter = Offset(rect.centerX, scanY)
            val beadRadius = min(rect.width * 0.42f, 7f)

            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = beadRadius * 2.2f,
                center = scanCenter,
            )
            drawCircle(
                color = Color.White,
                radius = beadRadius,
                center = scanCenter,
            )
        }
    }

    private fun DrawScope.drawRotatingBar(bar: Obstacle.RotatingBar) {
        val halfLen = bar.length / 2f
        val dir = Vec2.fromAngle(bar.currentAngleRad, 1f)
        val p1 = bar.center - (dir * halfLen)
        val p2 = bar.center + (dir * halfLen)
        val (accentColor, bodyColor) = getThemeColors(bar.theme)

        // 1. Outer Wide Ambient Aura
        drawLine(
            color = accentColor.copy(alpha = 0.18f),
            start = Offset(p1.x, p1.y),
            end = Offset(p2.x, p2.y),
            strokeWidth = bar.barThickness * 2.5f,
            cap = StrokeCap.Round,
        )

        // 2. Base Laser Beam
        drawLine(
            color = bodyColor.copy(alpha = 0.85f),
            start = Offset(p1.x, p1.y),
            end = Offset(p2.x, p2.y),
            strokeWidth = bar.barThickness,
            cap = StrokeCap.Round,
        )

        // 3. Vibrant Accent Core Strip
        drawLine(
            color = accentColor.copy(alpha = 0.95f),
            start = Offset(p1.x, p1.y),
            end = Offset(p2.x, p2.y),
            strokeWidth = bar.barThickness * 0.45f,
            cap = StrokeCap.Round,
        )

        // 4. White High-Voltage Centerline
        val insetDir = dir * 8f
        val p1Inner = p1 + insetDir
        val p2Inner = p2 - insetDir
        drawLine(
            color = Color.White.copy(alpha = 0.90f),
            start = Offset(p1Inner.x, p1Inner.y),
            end = Offset(p2Inner.x, p2Inner.y),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round,
        )

        // 5. Dual Tip Emitters
        val tipOrbs = listOf(p1, p2)
        for (orbPos in tipOrbs) {
            val centerOff = Offset(orbPos.x, orbPos.y)
            // Outer glow
            drawCircle(
                color = accentColor.copy(alpha = 0.35f),
                radius = bar.tipRadius * 1.8f,
                center = centerOff,
            )
            // Middle ring
            drawCircle(
                color = accentColor,
                radius = bar.tipRadius * 1.1f,
                center = centerOff,
                style = Stroke(width = 1.8f),
            )
            // Solid white emitter core
            drawCircle(
                color = Color.White,
                radius = 3.5f,
                center = centerOff,
            )
        }

        // 6. Central Rotating Reactor Hub
        val centerOff = Offset(bar.center.x, bar.center.y)
        drawCircle(
            color = accentColor.copy(alpha = 0.25f),
            radius = 16f,
            center = centerOff,
        )
        drawCircle(
            color = bodyColor,
            radius = 10f,
            center = centerOff,
        )
        drawCircle(
            color = accentColor,
            radius = 10f,
            center = centerOff,
            style = Stroke(width = 2f),
        )
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = centerOff,
        )
    }

    private fun DrawScope.drawMovingBlock(block: Obstacle.MovingBlock) {
        val (accentColor, bodyColor) = getThemeColors(block.theme)
        val topLeft = Offset(block.position.x - block.size.x / 2f, block.position.y - block.size.y / 2f)
        val cornerRadius = CornerRadius(8f, 8f)

        // 1. Kinetic Ambient Halo
        drawRoundRect(
            color = accentColor.copy(alpha = 0.15f),
            topLeft = Offset(topLeft.x - 5f, topLeft.y - 5f),
            size = Size(block.size.x + 10f, block.size.y + 10f),
            cornerRadius = CornerRadius(12f, 12f),
        )

        // 2. Block Body Plate
        drawRoundRect(
            color = bodyColor.copy(alpha = 0.90f),
            topLeft = topLeft,
            size = Size(block.size.x, block.size.y),
            cornerRadius = cornerRadius,
        )

        // 3. Upper Gloss Split
        drawRoundRect(
            color = accentColor.copy(alpha = 0.60f),
            topLeft = topLeft,
            size = Size(block.size.x, block.size.y * 0.5f),
            cornerRadius = cornerRadius,
        )

        // 4. White Top Specular Curve
        drawLine(
            color = Color.White.copy(alpha = 0.70f),
            start = Offset(topLeft.x + 6f, topLeft.y + 1.5f),
            end = Offset(topLeft.x + block.size.x - 6f, topLeft.y + 1.5f),
            strokeWidth = 1.2f,
        )

        // 5. Perimeter Neon Stroke
        drawRoundRect(
            color = accentColor,
            topLeft = topLeft,
            size = Size(block.size.x, block.size.y),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.8f),
        )

        // 6. Central Kinetic Momentum Indicator
        val center = Offset(block.position.x, block.position.y)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(center.x - 10f, center.y - 3f),
            size = Size(20f, 6f),
            cornerRadius = CornerRadius(3f, 3f),
        )
    }

    private fun DrawScope.drawDangerZone(zone: Obstacle.DangerZone) {
        val rect = zone.rect
        val accent = Color(0xFFFF3D00)
        val pulse = zone.pulsePhase
        val cornerRadius = CornerRadius(12f, 12f)

        // 1. Soft Outer Danger Glow (Pulsing)
        val glowAlpha = 0.06f + (pulse * 0.06f)
        drawRoundRect(
            color = accent.copy(alpha = glowAlpha),
            topLeft = Offset(rect.left - 8f, rect.top - 8f),
            size = Size(rect.width + 16f, rect.height + 16f),
            cornerRadius = CornerRadius(16f, 16f),
        )

        // 2. Interior Wash
        val washAlpha = 0.08f + (pulse * 0.08f)
        drawRoundRect(
            color = accent.copy(alpha = washAlpha),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = cornerRadius,
        )

        // 3. Subtle Internal Force-Field Grid
        val gridColor = accent.copy(alpha = 0.18f)
        val gridStep = 16f
        var gx = rect.left + gridStep
        while (gx < rect.right - 4f) {
            drawLine(
                color = gridColor,
                start = Offset(gx, rect.top + 6f),
                end = Offset(gx, rect.bottom - 6f),
                strokeWidth = 1f,
            )
            gx += gridStep
        }
        var gy = rect.top + gridStep
        while (gy < rect.bottom - 4f) {
            drawLine(
                color = gridColor,
                start = Offset(rect.left + 6f, gy),
                end = Offset(rect.right - 6f, gy),
                strokeWidth = 1f,
            )
            gy += gridStep
        }

        // 4. Perimeter Rounded Boundary
        drawRoundRect(
            color = accent.copy(alpha = 0.75f + pulse * 0.20f),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.6f),
        )

        // 5. Four Cyber Corner Brackets (L-shapes)
        val bLen = 14f
        val bGap = 3f
        val bracketColor = Color(0xFFFF6E40)
        val bracketStroke = 2.4f

        // Top-Left
        drawLine(bracketColor, Offset(rect.left + bGap, rect.top + bGap), Offset(rect.left + bGap + bLen, rect.top + bGap), bracketStroke)
        drawLine(bracketColor, Offset(rect.left + bGap, rect.top + bGap), Offset(rect.left + bGap, rect.top + bGap + bLen), bracketStroke)

        // Top-Right
        drawLine(bracketColor, Offset(rect.right - bGap, rect.top + bGap), Offset(rect.right - bGap - bLen, rect.top + bGap), bracketStroke)
        drawLine(bracketColor, Offset(rect.right - bGap, rect.top + bGap), Offset(rect.right - bGap, rect.top + bGap + bLen), bracketStroke)

        // Bottom-Left
        drawLine(bracketColor, Offset(rect.left + bGap, rect.bottom - bGap), Offset(rect.left + bGap + bLen, rect.bottom - bGap), bracketStroke)
        drawLine(bracketColor, Offset(rect.left + bGap, rect.bottom - bGap), Offset(rect.left + bGap, rect.bottom - bGap - bLen), bracketStroke)

        // Bottom-Right
        drawLine(bracketColor, Offset(rect.right - bGap, rect.bottom - bGap), Offset(rect.right - bGap - bLen, rect.bottom - bGap), bracketStroke)
        drawLine(bracketColor, Offset(rect.right - bGap, rect.bottom - bGap), Offset(rect.right - bGap, rect.bottom - bGap - bLen), bracketStroke)

        // 6. Sweeping Scanline
        val scanY = rect.top + 4f + (rect.height - 8f) * pulse
        drawLine(
            color = Color(0xFFFF9E80).copy(alpha = 0.85f),
            start = Offset(rect.left + 8f, scanY),
            end = Offset(rect.right - 8f, scanY),
            strokeWidth = 2f,
        )
    }

    private fun DrawScope.drawTeleporter(tele: Obstacle.Teleporter) {
        val colorA = Color(0xFF00E5FF) // Gate A Cyan
        val colorB = Color(0xFFFF0077) // Gate B Pink

        drawGate(tele.posA, tele.radius, tele.rotationPhaseA, colorA, "A")
        drawGate(tele.posB, tele.radius, tele.rotationPhaseB, colorB, "B")
    }

    private fun DrawScope.drawGate(
        pos: Vec2,
        radius: Float,
        rotationPhase: Float,
        accentColor: Color,
        gateLabel: String,
    ) {
        val center = Offset(pos.x, pos.y)

        // 1. Concentric Ambient Halo Rings
        for (i in 4 downTo 1) {
            val r = radius + (i * 5f)
            val alpha = 0.04f + (i * 0.03f)
            drawCircle(
                color = accentColor.copy(alpha = alpha),
                radius = r,
                center = center,
                style = Stroke(width = 2f),
            )
        }

        // 2. Radial Energy Disc
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.90f),
                    accentColor.copy(alpha = 0.65f),
                    accentColor.copy(alpha = 0.15f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius * 1.3f,
            ),
            radius = radius * 1.3f,
            center = center,
        )

        // 3. Crisp Gate Perimeter Ring + Inner Core Ring
        drawCircle(
            color = accentColor,
            radius = radius,
            center = center,
            style = Stroke(width = 2.4f),
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.60f),
            radius = radius - 6f,
            center = center,
            style = Stroke(width = 1.2f),
        )

        // 4. Rotating Radial Orbital Spokes (8 Spokes)
        val spokeCount = 8
        val spokeLen = 8f
        val angleStep = (2 * PI / spokeCount).toFloat()
        for (i in 0 until spokeCount) {
            val a = rotationPhase + (i * angleStep)
            val x1 = pos.x + cos(a) * (radius - 1f)
            val y1 = pos.y + sin(a) * (radius - 1f)
            val x2 = pos.x + cos(a) * (radius + spokeLen)
            val y2 = pos.y + sin(a) * (radius + spokeLen)

            drawLine(
                color = accentColor.copy(alpha = 0.85f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }

        // 5. White Core Singularity Dot
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = center,
        )
    }

    private fun DrawScope.drawShark(shark: Obstacle.Shark) {
        val center = Offset(shark.position.x, shark.position.y)
        val deg = shark.angleRad * (180f / PI.toFloat())

        rotate(degrees = deg, pivot = center) {
            val sc = shark.radius / 22f
            val wag = sin(shark.tailPhase) * 6f * sc

            // 1. Soft aquatic predator aura
            drawCircle(
                color = Color(0x3300E5FF),
                radius = shark.radius * 1.8f,
                center = center,
            )

            // 2. Body Path (Apex predator silhouette facing +X)
            val bodyPath = Path().apply {
                // Snout / Head
                moveTo(center.x + 32f * sc, center.y)
                // Top curve to dorsal fin
                cubicTo(
                    center.x + 18f * sc,
                    center.y - 12f * sc,
                    center.x + 4f * sc,
                    center.y - 16f * sc,
                    center.x - 6f * sc,
                    center.y - 14f * sc,
                )
                // Dorsal fin peak
                lineTo(center.x - 12f * sc, center.y - 26f * sc)
                lineTo(center.x - 16f * sc, center.y - 12f * sc)
                // Peduncle to tail
                cubicTo(
                    center.x - 26f * sc,
                    center.y - 8f * sc,
                    center.x - 36f * sc,
                    center.y - 4f * sc + wag * 0.5f,
                    center.x - 44f * sc,
                    center.y + wag,
                )
                // Upper caudal fin lobe
                lineTo(center.x - 56f * sc, center.y - 18f * sc + wag * 1.2f)
                // Caudal fin notch
                lineTo(center.x - 48f * sc, center.y + wag)
                // Lower caudal fin lobe
                lineTo(center.x - 54f * sc, center.y + 16f * sc + wag * 1.2f)
                // Lower tail back to belly
                cubicTo(
                    center.x - 36f * sc,
                    center.y + 4f * sc + wag * 0.5f,
                    center.x - 24f * sc,
                    center.y + 10f * sc,
                    center.x - 8f * sc,
                    center.y + 14f * sc,
                )
                // Pectoral fin
                lineTo(center.x + 2f * sc, center.y + 24f * sc)
                lineTo(center.x + 8f * sc, center.y + 12f * sc)
                // Throat to snout
                cubicTo(
                    center.x + 20f * sc,
                    center.y + 8f * sc,
                    center.x + 28f * sc,
                    center.y + 4f * sc,
                    center.x + 32f * sc,
                    center.y,
                )
                close()
            }

            // Draw main shark body with metallic ocean gradient
            drawPath(
                path = bodyPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0D3349),
                        Color(0xFF1E5B7A),
                        Color(0xFF3892B7),
                        Color(0xFF6ED4F0),
                    ),
                    startX = center.x - 56f * sc,
                    endX = center.x + 32f * sc,
                ),
            )

            // Glowing neon predator edge
            drawPath(
                path = bodyPath,
                color = Color(0xFF5CF0FF),
                style = Stroke(width = 1.6f),
            )

            // Gill slits (3 subtle glowing lines)
            for (g in 0..2) {
                val gx = center.x + (6f - g * 5f) * sc
                drawLine(
                    color = Color(0x995CF0FF),
                    start = Offset(gx, center.y - 6f * sc),
                    end = Offset(gx - 2f * sc, center.y + 6f * sc),
                    strokeWidth = 1.2f,
                )
            }

            // Predator glowing eyes
            drawCircle(
                color = Color(0xFFFFFFFF),
                radius = 2.4f * sc,
                center = Offset(center.x + 20f * sc, center.y - 5f * sc),
            )
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = 4.2f * sc,
                center = Offset(center.x + 20f * sc, center.y - 5f * sc),
                style = Stroke(width = 1f),
            )
        }
    }

    private fun DrawScope.drawForestProwler(prowler: Obstacle.ForestProwler) {
        val center = Offset(prowler.position.x, prowler.position.y)
        val deg = prowler.angleRad * (180f / PI.toFloat())

        rotate(degrees = deg, pivot = center) {
            val sc = prowler.radius / 33f
            val stride = prowler.stridePhase

            // 1. Subtle Bio-Aura Glow (Emerald Forest Mist)
            drawCircle(
                color = Color(0x3330FF9E),
                radius = 36f * sc,
                center = center,
            )

            // 2. Sinuous Swaying Feline Tail (curves behind from -16f to -38f)
            val tailWag1 = sin(stride * 0.8f) * 6f * sc
            val tailWag2 = sin(stride * 0.8f + 0.8f) * 10f * sc
            val tailBase = Offset(center.x - 16f * sc, center.y)
            val tailMid = Offset(center.x - 28f * sc, center.y + tailWag1)
            val tailTip = Offset(center.x - 38f * sc, center.y + tailWag2)
            drawLine(
                color = Color(0xFF0D3322),
                start = tailBase,
                end = tailMid,
                strokeWidth = 3.2f * sc,
            )
            drawLine(
                color = Color(0xFF16593B),
                start = tailMid,
                end = tailTip,
                strokeWidth = 2.4f * sc,
            )
            // Bioluminescent emerald tuft at tail tip
            drawCircle(
                color = Color(0xFF30FF9E),
                radius = 3f * sc,
                center = tailTip,
            )

            // 3. Four Articulated Prowling Paws (Quadrupedal Stride)
            val flStride = sin(stride) * 8f * sc
            val frStride = sin(stride + PI.toFloat()) * 8f * sc
            val rlStride = sin(stride + PI.toFloat()) * 7f * sc
            val rrStride = sin(stride) * 7f * sc

            // Rear Left Paw
            drawCircle(color = Color(0xFF092418), radius = 4f * sc, center = Offset(center.x - 10f * sc + rlStride, center.y - 14f * sc))
            drawCircle(color = Color(0xFF30FF9E), radius = 1.5f * sc, center = Offset(center.x - 10f * sc + rlStride, center.y - 14f * sc))

            // Rear Right Paw
            drawCircle(color = Color(0xFF092418), radius = 4f * sc, center = Offset(center.x - 10f * sc + rrStride, center.y + 14f * sc))
            drawCircle(color = Color(0xFF30FF9E), radius = 1.5f * sc, center = Offset(center.x - 10f * sc + rrStride, center.y + 14f * sc))

            // Front Left Paw
            drawCircle(color = Color(0xFF0E3826), radius = 4.5f * sc, center = Offset(center.x + 12f * sc + flStride, center.y - 14f * sc))
            drawCircle(color = Color(0xFF30FF9E), radius = 1.8f * sc, center = Offset(center.x + 12f * sc + flStride, center.y - 14f * sc))

            // Front Right Paw
            drawCircle(color = Color(0xFF0E3826), radius = 4.5f * sc, center = Offset(center.x + 12f * sc + frStride, center.y + 14f * sc))
            drawCircle(color = Color(0xFF30FF9E), radius = 1.8f * sc, center = Offset(center.x + 12f * sc + frStride, center.y + 14f * sc))

            // 4. Muscular Sleek Body (Torso & Flank)
            drawOval(
                color = Color(0xFF092418),
                topLeft = Offset(center.x - 18f * sc, center.y - 12f * sc),
                size = Size(34f * sc, 24f * sc),
            )
            drawOval(
                color = Color(0xFF144D34),
                topLeft = Offset(center.x - 14f * sc, center.y - 8f * sc),
                size = Size(26f * sc, 16f * sc),
            )

            // Bioluminescent flank stripes (Panther Rosettes / Jungle Markings)
            for (s in -1..1) {
                val stripeX = center.x + s * 7f * sc
                drawLine(
                    color = Color(0xFF30FF9E),
                    start = Offset(stripeX, center.y - 9f * sc),
                    end = Offset(stripeX + 2f * sc, center.y - 3f * sc),
                    strokeWidth = 1.6f * sc,
                )
                drawLine(
                    color = Color(0xFF30FF9E),
                    start = Offset(stripeX, center.y + 9f * sc),
                    end = Offset(stripeX + 2f * sc, center.y + 3f * sc),
                    strokeWidth = 1.6f * sc,
                )
            }

            // 5. Panther Head (Forward at +X)
            val headCenter = Offset(center.x + 16f * sc, center.y)
            drawCircle(
                color = Color(0xFF0E3826),
                radius = 11f * sc,
                center = headCenter,
            )

            // Pointed Predator Ears
            val earL = Path().apply {
                moveTo(headCenter.x - 2f * sc, headCenter.y - 8f * sc)
                lineTo(headCenter.x - 1f * sc, headCenter.y - 16f * sc)
                lineTo(headCenter.x + 5f * sc, headCenter.y - 7f * sc)
                close()
            }
            drawPath(earL, color = Color(0xFF092418))
            val earR = Path().apply {
                moveTo(headCenter.x - 2f * sc, headCenter.y + 8f * sc)
                lineTo(headCenter.x - 1f * sc, headCenter.y + 16f * sc)
                lineTo(headCenter.x + 5f * sc, headCenter.y + 7f * sc)
                close()
            }
            drawPath(earR, color = Color(0xFF092418))

            // Muzzle / Snout
            drawOval(
                color = Color(0xFF061810),
                topLeft = Offset(headCenter.x + 4f * sc, headCenter.y - 5f * sc),
                size = Size(8f * sc, 10f * sc),
            )

            // Piercing Glowing Amber Eyes with vertical slit
            drawCircle(
                color = Color(0xFFFFD700),
                radius = 2.2f * sc,
                center = Offset(headCenter.x + 5f * sc, headCenter.y - 4f * sc),
            )
            drawCircle(
                color = Color(0xFFFFD700),
                radius = 2.2f * sc,
                center = Offset(headCenter.x + 5f * sc, headCenter.y + 4f * sc),
            )
            drawLine(
                color = Color(0xFF000000),
                start = Offset(headCenter.x + 5f * sc, headCenter.y - 5.2f * sc),
                end = Offset(headCenter.x + 5f * sc, headCenter.y - 2.8f * sc),
                strokeWidth = 1f * sc,
            )
            drawLine(
                color = Color(0xFF000000),
                start = Offset(headCenter.x + 5f * sc, headCenter.y + 2.8f * sc),
                end = Offset(headCenter.x + 5f * sc, headCenter.y + 5.2f * sc),
                strokeWidth = 1f * sc,
            )
        }
    }

    private fun DrawScope.drawIsopod(isopod: Obstacle.Isopod) {
        val center = Offset(isopod.position.x, isopod.position.y)
        val deg = isopod.angleRad * (180f / PI.toFloat())

        rotate(degrees = deg, pivot = center) {
            val sc = isopod.radius / 33f
            val legPhase = isopod.legPhase

            // 1. Crawling articulated legs (7 pairs along left & right sides)
            for (i in 0 until 7) {
                val legX = center.x + (16f - i * 6f) * sc
                val legWag = sin(legPhase + i * 0.8f) * 4f * sc

                // Left leg (top in local frame)
                val lFoot = Offset(legX - 2f * sc, center.y - (18f + legWag) * sc)
                val lKnee = Offset(legX + 2f * sc, center.y - 12f * sc)
                drawLine(
                    color = Color(0xFF8AB4CD),
                    start = Offset(legX, center.y - 6f * sc),
                    end = lKnee,
                    strokeWidth = 1.6f,
                )
                drawLine(
                    color = Color(0xFFBCE3F5),
                    start = lKnee,
                    end = lFoot,
                    strokeWidth = 1.4f,
                )

                // Right leg (bottom in local frame)
                val rFoot = Offset(legX - 2f * sc, center.y + (18f - legWag) * sc)
                val rKnee = Offset(legX + 2f * sc, center.y + 12f * sc)
                drawLine(
                    color = Color(0xFF8AB4CD),
                    start = Offset(legX, center.y + 6f * sc),
                    end = rKnee,
                    strokeWidth = 1.6f,
                )
                drawLine(
                    color = Color(0xFFBCE3F5),
                    start = rKnee,
                    end = rFoot,
                    strokeWidth = 1.4f,
                )
            }

            // 2. Twin Sensory Antennae at head (+X)
            val antWag = sin(legPhase * 1.5f) * 3f * sc
            drawLine(
                color = Color(0xFFE0F7FF),
                start = Offset(center.x + 22f * sc, center.y - 4f * sc),
                end = Offset(center.x + 36f * sc, center.y - (10f + antWag) * sc),
                strokeWidth = 1.2f,
            )
            drawLine(
                color = Color(0xFFE0F7FF),
                start = Offset(center.x + 22f * sc, center.y + 4f * sc),
                end = Offset(center.x + 36f * sc, center.y + (10f - antWag) * sc),
                strokeWidth = 1.2f,
            )

            // 3. Segmented Overlapping Carapace Plates
            val segments = 7
            for (i in segments - 1 downTo 0) {
                val segX = center.x + (16f - i * 6f) * sc
                val segWidth = (14f - i * 0.8f) * sc
                val segHeight = (24f - i * 1.6f) * sc
                val plateColor = if (i == 0) Color(0xFF334B5C) else Color(0xFF223544)

                drawRoundRect(
                    color = plateColor,
                    topLeft = Offset(segX - segWidth / 2f, center.y - segHeight / 2f),
                    size = Size(segWidth, segHeight),
                    cornerRadius = CornerRadius(4f * sc, 4f * sc),
                )
                drawRoundRect(
                    color = Color(0xFF70A8C7).copy(alpha = 0.85f),
                    topLeft = Offset(segX - segWidth / 2f, center.y - segHeight / 2f),
                    size = Size(segWidth, segHeight),
                    cornerRadius = CornerRadius(4f * sc, 4f * sc),
                    style = Stroke(width = 1.2f),
                )
            }

            // 4. Head Shell Dome (+X)
            drawRoundRect(
                color = Color(0xFF4A6B82),
                topLeft = Offset(center.x + 14f * sc, center.y - 9f * sc),
                size = Size(10f * sc, 18f * sc),
                cornerRadius = CornerRadius(5f * sc, 5f * sc),
            )
            drawRoundRect(
                color = Color(0xFFA6DCF5),
                topLeft = Offset(center.x + 14f * sc, center.y - 9f * sc),
                size = Size(10f * sc, 18f * sc),
                cornerRadius = CornerRadius(5f * sc, 5f * sc),
                style = Stroke(width = 1.4f),
            )

            // Compound eyes
            drawCircle(Color(0xFF0B1720), radius = 2f * sc, center = Offset(center.x + 19f * sc, center.y - 5f * sc))
            drawCircle(Color(0xFF0B1720), radius = 2f * sc, center = Offset(center.x + 19f * sc, center.y + 5f * sc))
        }
    }

    private fun DrawScope.drawCyclone(cyclone: Obstacle.Cyclone) {
        val cx = cyclone.position.x
        val ecy = cyclone.position.y
        val radius = cyclone.visualRadius
        val spin = cyclone.spinDir
        val phi = cyclone.spinAngle
        val eye = radius * 0.082f

        // 1. Three-layer deep dark shadow whirlpool base
        drawCircle(
            color = Color(0x02, 0x09, 0x14, 0x61), // alpha ~ 0.38f
            radius = radius * 1.08f,
            center = Offset(cx, ecy),
        )
        drawCircle(
            color = Color(0x0a, 0x25, 0x40, 0x38), // alpha ~ 0.22f
            radius = radius * 0.98f,
            center = Offset(cx, ecy),
        )
        drawCircle(
            color = Color(0x1a, 0x5a, 0x78, 0x1A), // alpha ~ 0.10f
            radius = radius * 0.88f,
            center = Offset(cx, ecy),
        )

        // 2. Nine procedural logarithmic spiral arms
        val arms = 9
        val steps = 22
        val turns = 1.55f
        for (a in 0 until arms) {
            val arm0 = phi * spin + (a.toFloat() / arms) * 2f * PI.toFloat()
            val armColor = if (a % 2 == 1) Color(0xFF8ED4F0).copy(alpha = 0.50f) else Color(0xFF3C6F8C).copy(alpha = 0.50f)

            // Outer primary spiral arm
            val path1 = Path().apply {
                var first = true
                for (i in 0..steps) {
                    val u = i.toFloat() / steps
                    val rho = eye + u.pow(0.9f) * (radius * 0.93f - eye)
                    val ang = arm0 + u * turns * 2f * PI.toFloat()
                    val px = cx + cos(ang) * rho
                    val py = ecy + sin(ang) * rho
                    if (first) {
                        moveTo(px, py)
                        first = false
                    } else {
                        lineTo(px, py)
                    }
                }
            }
            drawPath(path = path1, color = armColor, style = Stroke(width = 2.7f, cap = StrokeCap.Round))

            // Inner highlight spiral arm
            val path2 = Path().apply {
                var first = true
                for (i in 0..steps) {
                    val u = i.toFloat() / steps
                    val rho = eye + u.pow(0.9f) * (radius * 0.88f - eye)
                    val ang = arm0 + u * turns * 2f * PI.toFloat() + 0.14f
                    val px = cx + cos(ang) * rho
                    val py = ecy + sin(ang) * rho
                    if (first) {
                        moveTo(px, py)
                        first = false
                    } else {
                        lineTo(px, py)
                    }
                }
            }
            drawPath(path = path2, color = Color(0xFFD8F6FF).copy(alpha = 0.16f), style = Stroke(width = 1.1f, cap = StrokeCap.Round))
        }

        // 3. Concentric Storm Rings
        drawCircle(
            color = Color(0xFFA8D8E8).copy(alpha = 0.36f),
            radius = radius * 0.76f,
            center = Offset(cx, ecy),
            style = Stroke(width = 3.2f),
        )
        drawCircle(
            color = Color(0xFF5AB0D0).copy(alpha = 0.16f),
            radius = radius * 0.96f,
            center = Offset(cx, ecy),
            style = Stroke(width = 1.4f),
        )

        // 4. Dark Eye Singularity Core
        drawCircle(
            color = Color(0xFF01060C).copy(alpha = 0.62f),
            radius = eye,
            center = Offset(cx, ecy),
        )
        drawCircle(
            color = Color(0xFF081C32).copy(alpha = 0.45f),
            radius = eye * 1.9f,
            center = Offset(cx, ecy),
        )

        // 5. Orbiting Water Droplets (20 droplets)
        val droplets = 20
        for (d in 0 until droplets) {
            val u = (d.toFloat() / droplets) * 2f * PI.toFloat() + phi * 0.5f
            val rad = radius * (0.92f + sin(d * 2.17f + phi) * 0.06f)
            val ox = cx + cos(u) * rad
            val oy = ecy + sin(u) * rad
            val alpha = 0.12f + (d % 3) * 0.04f
            drawOval(
                color = Color(0xFFC8E8F8).copy(alpha = alpha),
                topLeft = Offset(ox - 3.2f, oy - 2.1f),
                size = Size(6.4f, 4.2f),
            )
        }
    }

    private fun DrawScope.drawJellyfish(jelly: Obstacle.Jellyfish) {
        val center = Offset(jelly.position.x, jelly.position.y)
        val pulse = 1f + sin(jelly.pulsePhase * 2 * PI.toFloat()) * 0.14f
        val r = jelly.radius * pulse

        // 1. Soft Bioluminescent Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x885CF0FF),
                    Color(0x4423A0FF),
                    Color.Transparent,
                ),
                center = center,
                radius = r * 2.2f,
            ),
            radius = r * 2.2f,
            center = center,
        )

        // 2. Bell Dome
        val bellPath = Path().apply {
            moveTo(center.x - r, center.y)
            cubicTo(
                center.x - r,
                center.y - r * 1.3f,
                center.x + r,
                center.y - r * 1.3f,
                center.x + r,
                center.y,
            )
            // Undulating skirt
            val waveCount = 4
            val step = (2 * r) / waveCount
            for (w in 0 until waveCount) {
                val x1 = center.x + r - w * step
                val x2 = x1 - step
                val midX = (x1 + x2) / 2f
                val dip = if (w % 2 == 0) 3f else -2f
                quadraticTo(midX, center.y + dip, x2, center.y)
            }
            close()
        }

        drawPath(
            path = bellPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xDDFFFFFF),
                    Color(0xAA5CF0FF),
                    Color(0x8823A0FF),
                ),
                startY = center.y - r * 1.3f,
                endY = center.y + 4f,
            ),
        )
        drawPath(
            path = bellPath,
            color = Color(0xFFC8FAFF),
            style = Stroke(width = 1.4f),
        )

        // 3. Inner Organ Core
        drawCircle(
            color = Color(0xCCFF7AB8),
            radius = r * 0.35f,
            center = Offset(center.x, center.y - r * 0.4f),
        )

        // 4. Sinuous Trailing Tentacles (4 strands)
        for (t in 0..3) {
            val tx = center.x - r * 0.6f + (t * (r * 1.2f / 3f))
            val wave = sin(jelly.pulsePhase * 2 * PI.toFloat() + t) * 4f
            drawLine(
                color = Color(0x995CF0FF),
                start = Offset(tx, center.y + 2f),
                end = Offset(tx + wave * 0.5f, center.y + r * 2.4f),
                strokeWidth = 1.2f,
                cap = StrokeCap.Round,
            )
        }
    }

    private fun DrawScope.drawMagmaCrack(crack: Obstacle.MagmaCrack) {
        val cx = crack.center.x
        val cy = crack.center.y
        val coreRx = crack.coreRx
        val coreRy = crack.coreRy
        val arms = crack.arms
        val prep = 440f
        val prepStart = (crack.lethalFromMs - prep).coerceAtLeast(0f)
        val fadeMs = (crack.stopMs - crack.lethalUntilMs).toFloat().coerceAtLeast(1f)
        val age = crack.ageMs.toFloat()

        val fadeOut = if (age > crack.lethalUntilMs) {
            ((crack.stopMs - age) / fadeMs).coerceIn(0f, 1f)
        } else {
            1f
        }

        val pulse = 0.78f + 0.22f * sin(crack.pulsePhase * 2f * PI.toFloat() + crack.seed * 0.1f)
        val chargeProg = when (crack.phase) {
            "charge" -> ((age - prepStart) / prep).coerceIn(0f, 1f)
            "lethal" -> 1f
            else -> 0f
        }

        fun strokePoly(poly: List<Vec2>, width: Float, color: Color, alpha: Float, glow: Boolean = false) {
            if (poly.size < 2) return
            val path = Path().apply {
                moveTo(poly[0].x, poly[0].y)
                for (i in 1 until poly.size) lineTo(poly[i].x, poly[i].y)
            }
            if (glow) {
                drawPath(path = path, color = color.copy(alpha = alpha * 0.28f), style = Stroke(width = width * 1.75f, cap = StrokeCap.Round))
            }
            drawPath(path = path, color = color.copy(alpha = alpha), style = Stroke(width = width, cap = StrokeCap.Round))
        }

        val colAsh = Color(0xFF2A0806)
        val colM = Color(0xFFFF7020)
        val colHot = Color(0xFFFFC864)

        when (crack.phase) {
            "warn" -> {
                for (poly in arms) {
                    strokePoly(poly, 1.35f, colAsh, 0.48f * fadeOut, false)
                    strokePoly(poly, 0.75f, Color(0xFF6E2010), 0.32f * fadeOut, false)
                }
            }
            "charge" -> {
                val cg = chargeProg * fadeOut
                drawOval(
                    color = colM.copy(alpha = 0.08f * cg),
                    topLeft = Offset(cx - coreRx * 1.6f * cg, cy - coreRy * 1.5f * cg),
                    size = Size(coreRx * 3.2f * cg, coreRy * 3.0f * cg),
                )
                for (poly in arms) {
                    strokePoly(poly, 1.6f + chargeProg * 4.5f, colM, (0.35f + chargeProg * 0.4f) * fadeOut, true)
                    strokePoly(poly, 1.1f + chargeProg * 2.8f, colHot, (0.22f + chargeProg * 0.5f) * fadeOut, false)
                }
            }
            "lethal" -> {
                val lethalA = fadeOut * pulse
                // Central multi-tier molten glowing core
                drawOval(
                    color = Color(0xFFFF3A18).copy(alpha = 0.42f * lethalA),
                    topLeft = Offset(cx - coreRx * 2.1f, cy - coreRy * 1.95f),
                    size = Size(coreRx * 4.2f, coreRy * 3.9f),
                )
                drawOval(
                    color = Color(0xFFFF6600).copy(alpha = 0.28f * lethalA),
                    topLeft = Offset(cx - coreRx * 1.45f, cy - coreRy * 1.35f),
                    size = Size(coreRx * 2.9f, coreRy * 2.7f),
                )
                drawOval(
                    color = colHot.copy(alpha = 0.22f * lethalA),
                    topLeft = Offset(cx - coreRx * 0.72f, cy - coreRy * 0.62f),
                    size = Size(coreRx * 1.44f, coreRy * 1.24f),
                )

                // Fiery strokes across all branched arms
                for (poly in arms) {
                    strokePoly(poly, 15f, Color(0xFFFF3000), 0.26f * lethalA, false)
                    strokePoly(poly, 8.5f, colM, 0.52f * lethalA, false)
                    strokePoly(poly, 3.6f, colHot, 0.72f * lethalA, false)
                    strokePoly(poly, 1.35f, Color(0xFFFFF5CC), 0.58f * lethalA, false)

                    // Glowing ember nodes along arm points
                    for (qi in 2 until poly.size step 2) {
                        val p = poly[qi]
                        val dotR = 2.1f + ((sin((crack.seed + qi) * 12.9898 + qi * 1.414) * 43758.5453) % 1f).toFloat().let { kotlin.math.abs(it) } * 2.1f
                        drawCircle(color = colHot.copy(alpha = 0.4f * lethalA), radius = dotR, center = Offset(p.x, p.y))
                    }
                }
            }
            "fade" -> {
                for (poly in arms) {
                    strokePoly(poly, 2.2f, colAsh, 0.22f * fadeOut, false)
                    strokePoly(poly, 0.9f, Color(0xFF4A1810), 0.14f * fadeOut, false)
                }
            }
        }
    }

    private fun DrawScope.drawFallingEmber(ember: Obstacle.FallingEmber) {
        val center = Offset(ember.targetPos.x, ember.targetPos.y)
        val warnR = ember.warnRadius
        val tWarn = (ember.ageMs.toFloat() / ember.lethalAtMs.toFloat()).coerceIn(0f, 1f)

        // 1. Dark circular shadow crater disc base
        drawCircle(
            color = Color(0x55000000),
            radius = ember.impactRadius * 1.15f,
            center = center,
        )

        if (!ember.isLethal) {
            // Warning ring expanding with crosshairs
            val ringRadius = warnR + tWarn * 12f
            val ringAlpha = 0.22f + tWarn * 0.74f
            val strokeW = 2f + tWarn * 2.8f
            drawCircle(
                color = Color(0xFFFFB040).copy(alpha = ringAlpha),
                radius = ringRadius,
                center = center,
                style = Stroke(width = strokeW),
            )

            // Inner rising ember glow
            drawCircle(
                color = Color(0xFFFF5722).copy(alpha = 0.25f * tWarn),
                radius = warnR * tWarn,
                center = center,
            )

            // Crosshair markers
            val crossLen = 6f + tWarn * 4f
            val crossColor = Color(0xFFFFB040).copy(alpha = ringAlpha * 0.8f)
            drawLine(crossColor, Offset(center.x - ringRadius - crossLen, center.y), Offset(center.x - ringRadius + crossLen, center.y), strokeWidth = 1.6f)
            drawLine(crossColor, Offset(center.x + ringRadius - crossLen, center.y), Offset(center.x + ringRadius + crossLen, center.y), strokeWidth = 1.6f)
            drawLine(crossColor, Offset(center.x, center.y - ringRadius - crossLen), Offset(center.x, center.y - ringRadius + crossLen), strokeWidth = 1.6f)
            drawLine(crossColor, Offset(center.x, center.y + ringRadius - crossLen), Offset(center.x, center.y + ringRadius + crossLen), strokeWidth = 1.6f)
        } else {
            // Blazing lethal impact crater
            val hotTime = ember.ageMs - ember.lethalAtMs
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFFFEB3B),
                        Color(0xFFFF7020),
                        Color(0xFFFF3A18),
                        Color(0xFF2A0806),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = ember.impactRadius * 1.25f,
                ),
                radius = ember.impactRadius * 1.25f,
                center = center,
            )
            drawCircle(
                color = Color(0xFFFF7020).copy(alpha = 0.40f),
                radius = ember.impactRadius,
                center = center,
            )

            // Shock ring on detonation
            if (hotTime < 180L) {
                val shockAlpha = (1f - hotTime / 180f).coerceIn(0f, 1f) * 0.92f
                drawCircle(
                    color = Color(0xFFFFB040).copy(alpha = shockAlpha),
                    radius = warnR + 18f + (hotTime / 180f) * 8f,
                    center = center,
                    style = Stroke(width = 2.4f),
                )
            }
        }
    }

    private fun DrawScope.drawEscapeDoor(door: Obstacle.EscapeDoor) {
        val center = Offset(door.position.x, door.position.y)
        val w = door.width
        val h = door.height
        val pulse = 0.88f + 0.12f * sin(door.glowPhase)
        val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)

        // 1. Shimmering Golden Beacon Radiance
        val maxDim = maxOf(w, h)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD700).copy(alpha = 0.50f * pulse),
                    Color(0xFFFF9800).copy(alpha = 0.28f * pulse),
                    Color.Transparent,
                ),
                center = center,
                radius = maxDim * 1.4f,
            ),
            radius = maxDim * 1.4f,
            center = center,
        )

        // 2. Heavy Cyber Vault Outer Frame
        drawRect(
            color = Color(0xFFC28C18).copy(alpha = 0.92f * pulse),
            topLeft = Offset(topLeft.x - 3f, topLeft.y - 3f),
            size = Size(w + 6f, h + 6f),
        )
        drawRect(
            color = Color(0xFFFFE8A8).copy(alpha = 0.55f * pulse),
            topLeft = Offset(topLeft.x - 1f, topLeft.y - 1f),
            size = Size(w + 2f, h + 2f),
        )

        // 3. Body Plate
        drawRect(
            color = Color(0xFFFFC428).copy(alpha = 0.95f * pulse),
            topLeft = topLeft,
            size = Size(w, h),
        )

        // 4. Facets
        drawRect(
            color = Color(0xFFFFF2B0).copy(alpha = 0.38f * pulse),
            topLeft = Offset(topLeft.x + w * 0.08f, topLeft.y + h * 0.1f),
            size = Size(w * 0.35f, h * 0.22f),
        )
        drawRect(
            color = Color(0xFFFF9A14).copy(alpha = 0.42f * pulse),
            topLeft = Offset(topLeft.x + w * 0.52f, topLeft.y + h * 0.55f),
            size = Size(w * 0.38f, h * 0.28f),
        )

        // 5. Diagonal Shine Streak
        drawLine(
            color = Color.White.copy(alpha = 0.65f * pulse),
            start = Offset(topLeft.x + w * 0.18f, topLeft.y + h * 0.72f),
            end = Offset(topLeft.x + w * 0.88f, topLeft.y + h * 0.12f),
            strokeWidth = 2.4f,
        )

        // 6. Key Slot
        drawRoundRect(
            color = Color(0xFF2A1206).copy(alpha = 0.85f),
            topLeft = Offset(center.x - 5f, center.y + h * 0.08f),
            size = Size(10f, 14f),
            cornerRadius = CornerRadius(2f, 2f),
        )

        // 7. Beveled Border
        drawRoundRect(
            color = Color(0xFFFFE090).copy(alpha = 0.6f * pulse),
            topLeft = topLeft,
            size = Size(w, h),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 1.8f),
        )
    }

    private fun getThemeColors(theme: ObstacleTheme): Pair<Color, Color> = when (theme) {
        ObstacleTheme.CYAN -> Pair(Color(0xFF00E5FF), Color(0xFF0A2E44))
        ObstacleTheme.MAGENTA -> Pair(Color(0xFFFF0077), Color(0xFF3B0A24))
        ObstacleTheme.AMBER -> Pair(Color(0xFFFF9900), Color(0xFF3D2406))
        ObstacleTheme.EMERALD -> Pair(Color(0xFF00FF88), Color(0xFF0A2E1C))
        ObstacleTheme.VIOLET -> Pair(Color(0xFF9900FF), Color(0xFF280A44))
        ObstacleTheme.ICE_BLUE -> Pair(Color(0xFF7CE8FF), Color(0xFF0D253A))
        ObstacleTheme.MAGMA_FIRE -> Pair(Color(0xFFFF5722), Color(0xFF3E1205))
        ObstacleTheme.DEEP_OCEAN -> Pair(Color(0xFF00B0FF), Color(0xFF051C33))
    }
}
