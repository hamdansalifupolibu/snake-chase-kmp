package com.snakechase.app.engine

import com.snakechase.app.domain.model.Obstacle
import com.snakechase.app.domain.model.Vec2
import kotlin.math.min
import kotlin.math.pow

/**
 * Whirlpools and snow whirls don't kill: they grab whatever swims in (the orb *and* the snakes),
 * pull it inward and spin it around. The orb can fight its way out at full speed, a snake caught
 * mid-chase gets swept off its line, so luring snakes through a vortex is a real tactic.
 */
object Vortex {
    /** Inward pull at the eye, in arena units per second. */
    const val PULL = 95f

    /** Sideways spin at the eye, in arena units per second. */
    const val SWIRL = 250f

    /** The vortex reaches a little past its drawn edge. */
    const val REACH = 1.15f

    /** How far [pos] is carried by every active vortex during [dtSec]. */
    fun drift(pos: Vec2, obstacles: List<Obstacle>, dtSec: Float): Vec2 {
        var total = Vec2.ZERO
        for (obs in obstacles) {
            if (obs !is Obstacle.Cyclone || !obs.isActive) continue
            val reach = obs.visualRadius * REACH
            val toEye = obs.position - pos
            val d = toEye.length()
            if (d >= reach || d < 0.001f) continue
            val f = (1f - d / reach).pow(0.7f)
            val inward = toEye * (1f / d)
            // Same turning direction as the drawn spin (angle grows clockwise on screen).
            val spin = Vec2(inward.y, -inward.x) * obs.spinDir
            // Ease the pull off right at the eye so nothing jitters around the centre.
            val pull = PULL * min(1f, d / 30f)
            total += (inward * pull + spin * SWIRL) * (f * dtSec)
        }
        return total
    }
}
