package com.snakechase.app.engine

import com.snakechase.app.domain.model.Difficulty
import com.snakechase.app.domain.model.Obstacle
import com.snakechase.app.domain.model.Vec2
import com.snakechase.app.levels.ObstacleSetupConfig
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object ObstacleController {

    fun initializeObstacles(
        config: ObstacleSetupConfig,
        currentSubStageIndex: Int,
        soloStage: Boolean = false,
        difficulty: Difficulty = Difficulty.HARD,
        levelId: Int = 1,
    ): List<Obstacle> {
        val all = buildObstacles(config, currentSubStageIndex, soloStage)
        if (difficulty.obstacleShareFor(levelId) >= 1f) return all
        val kept = keptIds(config, difficulty, levelId)
        return all.filter { it.id in kept }
    }

    private val keptCache = mutableMapOf<Triple<ObstacleSetupConfig, Difficulty, Int>, Set<String>>()

    /**
     * Which obstacles a difficulty keeps: of each kind, an evenly spread [Difficulty.obstacleShare]
     * of them and never fewer than one, so every world keeps its full cast, just less crowded.
     * Chosen from the whole level (every stage unlocked) so the choice never changes mid-run.
     */
    fun keptIds(config: ObstacleSetupConfig, difficulty: Difficulty, levelId: Int = 1): Set<String> = keptCache.getOrPut(Triple(config, difficulty, levelId)) {
        buildObstacles(config, currentSubStageIndex = Int.MAX_VALUE, soloStage = false)
            .groupBy { it::class }
            .values
            .flatMap { group ->
                val keep = kotlin.math.ceil(group.size * difficulty.obstacleShareFor(levelId)).toInt().coerceIn(1, group.size)
                (0 until keep).map { i -> group[i * group.size / keep] }
            }
            .map { it.id }
            .toSet()
    }

    private fun buildObstacles(
        config: ObstacleSetupConfig,
        currentSubStageIndex: Int,
        soloStage: Boolean,
    ): List<Obstacle> {
        val list = mutableListOf<Obstacle>()

        fun isUnlocked(unlockStage: Int): Boolean = if (soloStage) unlockStage == currentSubStageIndex else currentSubStageIndex >= unlockStage

        // Walls
        for (w in config.walls) {
            if (isUnlocked(w.unlockSubStage)) {
                list.add(Obstacle.Wall(id = w.id, rect = w.rect, theme = w.theme))
            }
        }

        // Rotating Bars
        for (b in config.rotatingBars) {
            if (isUnlocked(b.unlockSubStage)) {
                val angularVel = (b.rpm * 2f * kotlin.math.PI.toFloat()) / 60f
                list.add(
                    Obstacle.RotatingBar(
                        id = b.id,
                        center = b.center,
                        length = b.length,
                        currentAngleRad = 0f,
                        angularVelocityRad = angularVel,
                        theme = b.theme,
                    ),
                )
            }
        }

        // Moving Blocks
        for (m in config.movingBlocks) {
            if (isUnlocked(m.unlockSubStage)) {
                val dir = (m.patrolB - m.patrolA).normalized()
                list.add(
                    Obstacle.MovingBlock(
                        id = m.id,
                        position = m.patrolA,
                        size = m.size,
                        patrolA = m.patrolA,
                        patrolB = m.patrolB,
                        velocity = dir * m.speed,
                        theme = m.theme,
                    ),
                )
            }
        }

        // Danger Zones
        for (d in config.dangerZones) {
            if (isUnlocked(d.unlockSubStage)) {
                list.add(Obstacle.DangerZone(id = d.id, rect = d.rect, theme = d.theme))
            }
        }

        // Teleporters
        for (t in config.teleporters) {
            if (isUnlocked(t.unlockSubStage)) {
                list.add(
                    Obstacle.Teleporter(
                        id = t.id,
                        posA = t.posA,
                        posB = t.posB,
                        radius = t.radius,
                        theme = t.theme,
                    ),
                )
            }
        }

        // Sharks (Level 3)
        for (s in config.sharks) {
            if (isUnlocked(s.unlockSubStage)) {
                val initialAngle = if (s.patrol.size > 1) {
                    val d = s.patrol[1] - s.patrol[0]
                    atan2(d.y, d.x)
                } else {
                    0f
                }
                list.add(
                    Obstacle.Shark(
                        id = s.id,
                        position = s.initialPos,
                        velocity = Vec2(cos(initialAngle), sin(initialAngle)) * s.speed,
                        radius = s.radius,
                        angleRad = initialAngle,
                        patrolPoints = s.patrol,
                        speed = s.speed,
                    ),
                )
            }
        }

        // Cyclones (Level 3)
        for (c in config.cyclones) {
            if (isUnlocked(c.unlockSubStage)) {
                list.add(
                    Obstacle.Cyclone(
                        id = c.id,
                        position = c.initialPos,
                        visualRadius = c.radius,
                        hitRadius = c.hitRadius,
                        spinSpeed = c.spinSpeed,
                        spinDir = c.spinDir,
                        patrolPoints = c.patrol,
                        speed = c.speed,
                    ),
                )
            }
        }

        // Jellyfish (Level 3)
        for (j in config.jellyfish) {
            if (isUnlocked(j.unlockSubStage)) {
                list.add(
                    Obstacle.Jellyfish(
                        id = j.id,
                        position = j.initialPos,
                        radius = j.radius,
                        patrolPoints = j.patrol,
                        speed = j.speed,
                    ),
                )
            }
        }

        // Forest Prowlers (Level 2: Neon Grove)
        for (fp in config.forestProwlers) {
            if (isUnlocked(fp.unlockSubStage)) {
                val initialAngle = if (fp.patrol.size > 1) {
                    val d = fp.patrol[1] - fp.patrol[0]
                    atan2(d.y, d.x)
                } else {
                    0f
                }
                list.add(
                    Obstacle.ForestProwler(
                        id = fp.id,
                        position = fp.initialPos,
                        radius = fp.radius,
                        angleRad = initialAngle,
                        patrolPoints = fp.patrol,
                        speed = fp.speed,
                    ),
                )
            }
        }

        // Isopods (Level 4)
        for (iso in config.isopods) {
            if (isUnlocked(iso.unlockSubStage)) {
                val initialAngle = if (iso.patrol.size > 1) {
                    val d = iso.patrol[1] - iso.patrol[0]
                    atan2(d.y, d.x)
                } else {
                    0f
                }
                list.add(
                    Obstacle.Isopod(
                        id = iso.id,
                        position = iso.initialPos,
                        radius = iso.radius,
                        angleRad = initialAngle,
                        patrolPoints = iso.patrol,
                        speed = iso.speed,
                    ),
                )
            }
        }

        // Magma Cracks (Level 5)
        for (mc in config.magmaCracks) {
            if (isUnlocked(mc.unlockSubStage)) {
                val seed = if (mc.seed != 0L) mc.seed else (mc.id.hashCode().toLong() and 0x7FFFFFFF)
                val ph = generatePotholeArms(mc.center.x, mc.center.y, seed)
                val staggerOffset = (seed % 1400L)
                list.add(
                    Obstacle.MagmaCrack(
                        id = mc.id,
                        center = mc.center,
                        arms = ph.arms,
                        coreRx = ph.coreRx,
                        coreRy = ph.coreRy,
                        coreR = ph.coreR,
                        seed = seed,
                        ageMs = staggerOffset,
                        lethalFromMs = 1800L,
                        lethalUntilMs = 4400L,
                        stopMs = 5160L,
                        phase = "warn",
                        width = mc.width,
                    ),
                )
            }
        }

        // Falling Embers (Level 5)
        for (fe in config.fallingEmbers) {
            if (isUnlocked(fe.unlockSubStage)) {
                val seed = fe.id.hashCode().toLong() and 0x7FFFFFFF
                val staggerOffset = (seed % 1200L)
                list.add(
                    Obstacle.FallingEmber(
                        id = fe.id,
                        targetPos = fe.targetPos,
                        warnRadius = fe.warnRadius,
                        impactRadius = fe.impactRadius,
                        ageMs = staggerOffset,
                        lethalAtMs = 860L,
                        lethalUntilMs = 3140L,
                        stopMs = 3660L,
                        isLethal = false,
                    ),
                )
            }
        }

        // Escape Door (Level 5 Final Eruption)
        val ed = config.escapeDoor
        if (ed != null && isUnlocked(ed.unlockSubStage)) {
            list.add(
                Obstacle.EscapeDoor(
                    id = ed.id,
                    position = ed.position,
                    width = ed.width,
                    height = ed.height,
                ),
            )
        }

        return list
    }

    fun generatePotholeArms(cx: Float, cy: Float, seed: Long): PotholeData {
        val random = kotlin.random.Random(seed)
        val extent = random.nextInt(66, 133).toFloat()
        val nArms = random.nextInt(5, 10)
        val arms = mutableListOf<List<Vec2>>()
        val golden = 2.3999632f
        val rot0 = (random.nextFloat() * 2f * kotlin.math.PI.toFloat())

        for (ai in 0 until nArms) {
            val baseAng = rot0 + ai * golden + (random.nextFloat() * 0.64f - 0.32f)
            val segs = random.nextInt(4, 10)
            val poly = mutableListOf<Vec2>()
            var x = cx
            var y = cy
            poly.add(Vec2(x, y))
            var curAng = baseAng
            val stepBase = extent / maxOf(segs, 1)

            for (s in 0 until segs) {
                curAng += (random.nextFloat() * 1.04f - 0.52f)
                val len = stepBase * (0.72f + random.nextFloat() * 0.56f)
                x += cos(curAng) * len + (random.nextFloat() * 12f - 6f)
                y += sin(curAng) * len + (random.nextFloat() * 12f - 6f)
                x = x.coerceIn(20f, 780f)
                y = y.coerceIn(20f, 580f)
                poly.add(Vec2(x, y))
            }
            arms.add(poly)

            // Branching chance
            if (random.nextFloat() < 0.44f && poly.size > 3) {
                val bi = random.nextInt(1, poly.size - 2)
                val bx0 = poly[bi].x
                val by0 = poly[bi].y
                var bAng = baseAng + (0.7f + random.nextFloat() * 0.9f) * (if (random.nextBoolean()) 1f else -1f)
                val bPoly = mutableListOf<Vec2>()
                bPoly.add(Vec2(bx0, by0))
                var bx = bx0
                var by = by0
                val bSegs = random.nextInt(2, 6)
                for (b in 0 until bSegs) {
                    bAng += (random.nextFloat() * 1.1f - 0.55f)
                    val bl = extent * (0.1f + random.nextFloat() * 0.16f)
                    bx += cos(bAng) * bl
                    by += sin(bAng) * bl
                    bx = bx.coerceIn(18f, 782f)
                    by = by.coerceIn(18f, 582f)
                    bPoly.add(Vec2(bx, by))
                }
                arms.add(bPoly)
            }
        }

        val coreR = extent * (0.14f + random.nextFloat() * 0.10f)
        val coreRx = coreR * (1.05f + random.nextFloat() * 0.37f)
        val coreRy = coreR * (0.78f + random.nextFloat() * 0.40f)

        return PotholeData(
            arms = arms,
            center = Vec2(cx, cy),
            coreR = coreR,
            coreRx = coreRx,
            coreRy = coreRy,
        )
    }

    data class PotholeData(
        val arms: List<List<Vec2>>,
        val center: Vec2,
        val coreR: Float,
        val coreRx: Float,
        val coreRy: Float,
    )

    fun updateObstacles(
        obstacles: List<Obstacle>,
        deltaTimeSec: Float,
        deltaTimeMs: Long,
    ): List<Obstacle> = obstacles.map { obs ->
        when (obs) {
            is Obstacle.RotatingBar -> {
                val newAngle = obs.currentAngleRad + (obs.angularVelocityRad * deltaTimeSec)
                obs.copy(currentAngleRad = newAngle)
            }
            is Obstacle.MovingBlock -> {
                var newPos = obs.position + (obs.velocity * deltaTimeSec)
                var newVel = obs.velocity

                val toB = obs.patrolB - obs.patrolA
                val toCurrent = newPos - obs.patrolA
                val distAtoB = toB.length()
                val proj = if (distAtoB > 0) (toCurrent.x * toB.x + toCurrent.y * toB.y) / (distAtoB * distAtoB) else 0f

                if (proj >= 1f) {
                    newPos = obs.patrolB
                    newVel = (obs.patrolA - obs.patrolB).normalized() * obs.velocity.length()
                } else if (proj <= 0f) {
                    newPos = obs.patrolA
                    newVel = (obs.patrolB - obs.patrolA).normalized() * obs.velocity.length()
                }
                obs.copy(position = newPos, velocity = newVel)
            }
            is Obstacle.DangerZone -> {
                val phase = (obs.pulsePhase + deltaTimeSec * 1.5f) % 1f
                obs.copy(pulsePhase = phase)
            }
            is Obstacle.Wall -> {
                val phase = (obs.scanlinePhase + deltaTimeSec * 0.8f) % 1f
                obs.copy(scanlinePhase = phase)
            }
            is Obstacle.Teleporter -> {
                val rotA = (obs.rotationPhaseA + deltaTimeSec * 2f) % (2 * kotlin.math.PI.toFloat())
                val rotB = (obs.rotationPhaseB - deltaTimeSec * 2f) % (2 * kotlin.math.PI.toFloat())
                val cd = if (obs.cooldownMs > 0) obs.cooldownMs - deltaTimeMs else 0L
                obs.copy(
                    rotationPhaseA = rotA,
                    rotationPhaseB = rotB,
                    cooldownMs = cd,
                )
            }
            is Obstacle.Shark -> {
                if (obs.patrolPoints.isEmpty()) {
                    obs
                } else {
                    val target = obs.patrolPoints[obs.currentPatrolIdx]
                    val diff = target - obs.position
                    val dist = diff.length()
                    val nextIdx = if (dist < 14f) (obs.currentPatrolIdx + 1) % obs.patrolPoints.size else obs.currentPatrolIdx
                    val dir = if (dist > 0.1f) diff.normalized() else Vec2(1f, 0f)
                    val angle = atan2(dir.y, dir.x)
                    val newPos = obs.position + dir * (obs.speed * deltaTimeSec)
                    val tail = obs.tailPhase + deltaTimeSec * 10f
                    obs.copy(
                        position = newPos,
                        velocity = dir * obs.speed,
                        angleRad = angle,
                        tailPhase = tail,
                        currentPatrolIdx = nextIdx,
                    )
                }
            }
            is Obstacle.ForestProwler -> {
                if (obs.patrolPoints.isEmpty()) {
                    obs
                } else {
                    val target = obs.patrolPoints[obs.currentPatrolIdx]
                    val diff = target - obs.position
                    val dist = diff.length()
                    val nextIdx = if (dist < 14f) (obs.currentPatrolIdx + 1) % obs.patrolPoints.size else obs.currentPatrolIdx
                    val dir = if (dist > 0.1f) diff.normalized() else Vec2(1f, 0f)
                    val angle = atan2(dir.y, dir.x)
                    val newPos = obs.position + dir * (obs.speed * deltaTimeSec)
                    val stride = obs.stridePhase + deltaTimeSec * 7f
                    obs.copy(
                        position = newPos,
                        angleRad = angle,
                        stridePhase = stride,
                        currentPatrolIdx = nextIdx,
                    )
                }
            }
            is Obstacle.Isopod -> {
                if (obs.patrolPoints.isEmpty()) {
                    obs
                } else {
                    val target = obs.patrolPoints[obs.currentPatrolIdx]
                    val diff = target - obs.position
                    val dist = diff.length()
                    val nextIdx = if (dist < 14f) (obs.currentPatrolIdx + 1) % obs.patrolPoints.size else obs.currentPatrolIdx
                    val dir = if (dist > 0.1f) diff.normalized() else Vec2(1f, 0f)
                    val angle = atan2(dir.y, dir.x)
                    val newPos = obs.position + dir * (obs.speed * deltaTimeSec)
                    val legs = obs.legPhase + deltaTimeSec * 6f
                    obs.copy(
                        position = newPos,
                        angleRad = angle,
                        legPhase = legs,
                        currentPatrolIdx = nextIdx,
                    )
                }
            }
            is Obstacle.Cyclone -> {
                val spin = (obs.spinAngle + obs.spinSpeed * obs.spinDir * deltaTimeSec * 1.5f) % (2 * kotlin.math.PI.toFloat())
                if (obs.patrolPoints.isEmpty()) {
                    obs.copy(spinAngle = spin)
                } else {
                    val target = obs.patrolPoints[obs.currentPatrolIdx]
                    val diff = target - obs.position
                    val dist = diff.length()
                    val nextIdx = if (dist < 28f) (obs.currentPatrolIdx + 1) % obs.patrolPoints.size else obs.currentPatrolIdx
                    val dir = if (dist > 0.1f) diff.normalized() else Vec2(1f, 0f)
                    val newX = (obs.position.x + dir.x * (obs.speed * deltaTimeSec)).coerceIn(60f, 740f)
                    val newY = (obs.position.y + dir.y * (obs.speed * deltaTimeSec)).coerceIn(60f, 540f)
                    obs.copy(
                        position = Vec2(newX, newY),
                        spinAngle = spin,
                        currentPatrolIdx = nextIdx,
                    )
                }
            }
            is Obstacle.Jellyfish -> {
                val pulse = (obs.pulsePhase + deltaTimeSec * 1.8f) % 1f
                if (obs.patrolPoints.isEmpty()) {
                    obs.copy(pulsePhase = pulse)
                } else {
                    val target = obs.patrolPoints[obs.currentPatrolIdx]
                    val diff = target - obs.position
                    val dist = diff.length()
                    val nextIdx = if (dist < 12f) (obs.currentPatrolIdx + 1) % obs.patrolPoints.size else obs.currentPatrolIdx
                    val dir = if (dist > 0.1f) diff.normalized() else Vec2(0f, 1f)
                    val newPos = obs.position + dir * (obs.speed * deltaTimeSec)
                    obs.copy(
                        position = newPos,
                        pulsePhase = pulse,
                        currentPatrolIdx = nextIdx,
                    )
                }
            }
            is Obstacle.MagmaCrack -> {
                val newAge = obs.ageMs + deltaTimeMs
                val prep = 440L
                val prepStart = obs.lethalFromMs - prep
                val currentPhase = when {
                    newAge >= obs.lethalFromMs && newAge <= obs.lethalUntilMs -> "lethal"
                    newAge >= prepStart && newAge < obs.lethalFromMs -> "charge"
                    newAge < prepStart -> "warn"
                    newAge < obs.stopMs -> "fade"
                    else -> "loop"
                }
                if (currentPhase == "loop") {
                    val nextSeed = (obs.seed + 37L) and 0x7FFFFFFF
                    val ph = generatePotholeArms(obs.center.x, obs.center.y, nextSeed)
                    obs.copy(
                        arms = ph.arms,
                        coreRx = ph.coreRx,
                        coreRy = ph.coreRy,
                        coreR = ph.coreR,
                        seed = nextSeed,
                        ageMs = 0L,
                        phase = "warn",
                    )
                } else {
                    obs.copy(
                        ageMs = newAge,
                        phase = currentPhase,
                        pulsePhase = (obs.pulsePhase + deltaTimeSec * 2f) % 1f,
                    )
                }
            }
            is Obstacle.FallingEmber -> {
                val newAge = obs.ageMs + deltaTimeMs
                if (newAge >= obs.stopMs) {
                    obs.copy(
                        ageMs = 0L,
                        timerMs = 0L,
                        isLethal = false,
                    )
                } else {
                    val lethal = newAge >= obs.lethalAtMs && newAge <= obs.lethalUntilMs
                    obs.copy(
                        ageMs = newAge,
                        timerMs = newAge,
                        isLethal = lethal,
                    )
                }
            }
            is Obstacle.EscapeDoor -> {
                val glow = (obs.glowPhase + deltaTimeSec * 3f) % (2 * kotlin.math.PI.toFloat())
                obs.copy(glowPhase = glow)
            }
        }
    }
}
