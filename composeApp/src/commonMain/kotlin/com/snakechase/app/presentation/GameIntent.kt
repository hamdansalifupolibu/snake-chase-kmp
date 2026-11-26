package com.snakechase.app.presentation

import com.snakechase.app.domain.model.UserSettings
import com.snakechase.app.domain.model.Vec2

sealed class GameIntent {
    // Player Input
    data class PlayerMove(val position: Vec2) : GameIntent()
    data class PlayerDragDelta(val delta: Vec2) : GameIntent()
    data class JoystickVector(val vector: Vec2) : GameIntent()
    object TriggerPhaseDash : GameIntent()
    object PauseGame : GameIntent()
    object ResumeGame : GameIntent()
    object RestartLevel : GameIntent()
    object ReturnToMenu : GameIntent()

    // Level Navigation
    data class SelectLevel(val levelId: Int) : GameIntent()
    object StartSelectedLevel : GameIntent()
    object StartDailyChallenge : GameIntent()
    data class CountdownTick(val count: Int) : GameIntent()
    object CountdownFinished : GameIntent()

    // Multiplayer Navigation
    object OpenMultiplayer : GameIntent()

    // Snake Hunt (play as the snake)
    object OpenSnakeHunt : GameIntent()

    // Frame Simulation
    data class FrameTick(val deltaMs: Long) : GameIntent()

    /** Mouse/trackpad: the orb heads for this arena point (no click needed). */
    /** Spend a heart and continue the run from just before the catch. */
    object Revive : GameIntent()

    /** Buy a full set of hearts with coins (store build). */
    object BuyHearts : GameIntent()

    /** The slow-motion catch replay finished: show the game-over panel. */
    object DeathReplayDone : GameIntent()

    data class PlayerAimAt(val pos: com.snakechase.app.domain.model.Vec2) : GameIntent()

    // Settings Overlay & Persistence
    object OpenSettings : GameIntent()
    object CloseSettings : GameIntent()
    data class UpdateSettings(val transform: (UserSettings) -> UserSettings) : GameIntent()
    object ResetProgress : GameIntent()

    // About & Credits Overlay
    object OpenAbout : GameIntent()
    object CloseAbout : GameIntent()

    // Leaderboard & Trophy Overlay
    object OpenLeaderboard : GameIntent()
    object CloseLeaderboard : GameIntent()

    // Snake Bestiary & Collection Overlay
    object OpenBestiary : GameIntent()
    object CloseBestiary : GameIntent()

    // Snake Skin Locker & Wardrobe Overlay
    object OpenSkinSelect : GameIntent()
    object CloseSkinSelect : GameIntent()
    data class EquipSkin(val skin: com.snakechase.app.domain.model.SnakeSkin) : GameIntent()
    data class BuySkin(val skin: com.snakechase.app.domain.model.SnakeSkin) : GameIntent()
    data class EquipOrb(val orb: com.snakechase.app.domain.model.OrbSkin) : GameIntent()
    data class BuyOrb(val orb: com.snakechase.app.domain.model.OrbSkin) : GameIntent()

    /** The first orb skin waiting in [com.snakechase.app.domain.model.GameState.newOrbSkins] was celebrated. */
    object OrbCelebrationSeen : GameIntent()

    // Player profile & daily reward
    data class SaveProfile(val name: String, val avatar: Int) : GameIntent()
    object OpenProfile : GameIntent()
    object CloseProfile : GameIntent()
    object DismissDailyReward : GameIntent()

    // Dev & Judge Testing Utilities
    object ToggleJudgeMode : GameIntent()
    object ToggleGodMode : GameIntent()
    data class SetDevTargetLevel(val levelId: Int) : GameIntent()
    data class JumpToSubStage(val subStageIndex: Int, val targetLevelId: Int? = null, val resetScore: Boolean = true) : GameIntent()
    data class StepSubStage(val delta: Int) : GameIntent()
    object ReplayCurrentSubStage : GameIntent()
    object ToggleDevSoloStageMode : GameIntent()
    object ToggleDevPanelCollapsed : GameIntent()
    object ToggleDevResetScore : GameIntent()
    object ForceWinLevel : GameIntent()
    object ForceDieNow : GameIntent()
    data class TriggerSpawn(val type: String) : GameIntent()
    object TriggerSplitSnake : GameIntent()
    object UnlockAllLevels : GameIntent()
}
