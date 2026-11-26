package com.snakechase.app.domain.model

data class BestiaryCard(
    val levelId: Int,
    val snakeName: String,
    val title: String,
    val species: String,
    val threatLevel: String,
    val description: String,
    val tacticalAdvice: String,
    val primaryColorArgb: Long,
    val accentColorArgb: Long,
    val isUnlocked: Boolean = false,
    val badgeSurvivor: Boolean = false, // Survived 3+ near-misses
    val badgeTamer: Boolean = false, // Survived 30s+ in the level
    val badgeConqueror: Boolean = false, // Completed the level
) {
    val totalBadgesEarned: Int
        get() = (if (badgeSurvivor) 1 else 0) + (if (badgeTamer) 1 else 0) + (if (badgeConqueror) 1 else 0)

    companion object {
        val ALL_CARDS = listOf(
            BestiaryCard(
                levelId = 1,
                snakeName = "Volt",
                title = "Circuit Stalker",
                species = "Cybernetic Serpent",
                threatLevel = "CLASS-1 CYBER PREDATOR",
                description = "Forged inside high-frequency neon conduits. Calculates predictive micro-vectors to cut off retreat lines across the cyber grid.",
                tacticalAdvice = "Cut sharply across grid intersections. Break line of sight to disrupt its acceleration ramp.",
                primaryColorArgb = 0xFF00E5FF,
                accentColorArgb = 0xFF7C4DFF,
            ),
            BestiaryCard(
                levelId = 2,
                snakeName = "Fern",
                title = "Spore Canopy Apex",
                species = "Bioluminescent Biomass",
                threatLevel = "PRIMORDIAL BIOMASS",
                description = "Camouflaged among roots and ancient luminescent moss. When pressured, its body fractures into a synchronized flanking triad.",
                tacticalAdvice = "Bait the flankers into sharp turns around root pillars. Don't linger near the spore rifts.",
                primaryColorArgb = 0xFF00E676,
                accentColorArgb = 0xFFFFD600,
            ),
            BestiaryCard(
                levelId = 3,
                snakeName = "Abyss",
                title = "Trench Leviathan",
                species = "Benthic Serpent",
                threatLevel = "ABYSSAL LEVIATHAN",
                description = "Hunts in the benthic abyssal trench where daylight has never penetrated. Moves in harmony with crushing whirlpool vortex currents.",
                tacticalAdvice = "Never get cornered against the seabed boundary when cyclones spawn. Use teleporters to reset its momentum.",
                primaryColorArgb = 0xFF2979FF,
                accentColorArgb = 0xFFFF4081,
            ),
            BestiaryCard(
                levelId = 4,
                snakeName = "Frost",
                title = "Glacial Wyrm",
                species = "Permafrost Wight",
                threatLevel = "CRYO-KINETIC ENTITY",
                description = "Encased in crystalline rime and razor permafrost. Surges with sub-zero speed bursts that freeze reaction windows.",
                tacticalAdvice = "Slip through the center gap between twin heads during Glacier Twin rather than fleeing toward arena corners.",
                primaryColorArgb = 0xFF80D8FF,
                accentColorArgb = 0xFFE040FB,
            ),
            BestiaryCard(
                levelId = 5,
                snakeName = "Inferno",
                title = "Caldera Juggernaut",
                species = "Molten Slag Behemoth",
                threatLevel = "EXTINCTION-CLASS THREAT",
                description = "The crucible's living furnace. Driven by relentless intercept logic, it will pursue prey until the arena caldera itself detonates.",
                tacticalAdvice = "When the blast door at (738, 56) unlocks at Stage 4, drop greed farming and sprint for the threshold.",
                primaryColorArgb = 0xFFFF3D00,
                accentColorArgb = 0xFFFFD600,
            ),
        )

        fun createCardList(
            unlockedLevels: Set<Int>,
            bestTimes: Map<Int, Long>,
            completedLevels: Set<Int>,
            bestScores: Map<Int, Int>,
        ): List<BestiaryCard> = ALL_CARDS.map { template ->
            val levelId = template.levelId
            val isUnlocked = unlockedLevels.contains(levelId) || (bestScores[levelId] ?: 0) > 0
            val bestTimeMs = bestTimes[levelId] ?: 0L
            val isCompleted = completedLevels.contains(levelId) || (bestScores[levelId] ?: 0) >= 100

            template.copy(
                isUnlocked = isUnlocked,
                badgeSurvivor = isUnlocked && (bestScores[levelId] ?: 0) > 0,
                badgeTamer = bestTimeMs >= 30_000L,
                badgeConqueror = isCompleted,
            )
        }
    }
}
