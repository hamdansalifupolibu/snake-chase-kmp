package com.snakechase.app.levels

object LevelRegistry {
    val ALL_LEVELS: List<LevelConfig> = listOf(
        Level1NeonDrift,
        Level2NeonGrove,
        Level3AbyssalChase,
        Level4CrystalTundra,
        Level5CrucibleCore,
    )

    fun getById(id: Int): LevelConfig = ALL_LEVELS.firstOrNull { it.id == id } ?: Level1NeonDrift
}
