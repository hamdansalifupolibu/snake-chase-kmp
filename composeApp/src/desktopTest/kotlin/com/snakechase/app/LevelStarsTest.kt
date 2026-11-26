package com.snakechase.app

import com.russhwolf.settings.MapSettings
import com.snakechase.app.domain.repository.SettingsRepositoryImpl
import com.snakechase.app.levels.LevelStars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelStarsTest {
    @Test
    fun clearing_earnsOneToThreeStars_byScoreShare() {
        assertEquals(1, LevelStars.forClear(10, 100))
        assertEquals(1, LevelStars.forClear(69, 100))
        assertEquals(2, LevelStars.forClear(70, 100))
        assertEquals(2, LevelStars.forClear(89, 100))
        assertEquals(3, LevelStars.forClear(90, 100))
        assertEquals(3, LevelStars.forClear(400, 400))
    }

    @Test
    fun stars_surviveRestart_keepTheBest_andResetClearsThem() {
        val storage = MapSettings()
        val repo = SettingsRepositoryImpl(storage)
        repo.saveLevelStars(1, 2)
        repo.saveLevelStars(1, 1) // a worse clear never lowers the rating
        repo.saveLevelStars(4, 3)
        assertEquals(mapOf(1 to 2, 4 to 3), repo.levelStars.value)

        // A fresh repository over the same storage = the app restarting.
        val reopened = SettingsRepositoryImpl(storage)
        assertEquals(mapOf(1 to 2, 4 to 3), reopened.levelStars.value)

        reopened.resetAllProgress()
        assertTrue(reopened.levelStars.value.isEmpty())
        assertTrue(SettingsRepositoryImpl(storage).levelStars.value.isEmpty())
    }
}
