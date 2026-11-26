package com.snakechase.app.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.snakechase.app.domain.model.HapticStyle

actual class HapticsManager(private val context: Context) {
    private var isEnabled: Boolean = true

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    actual fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    actual fun trigger(style: HapticStyle) {
        if (!isEnabled || vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (style) {
                HapticStyle.LIGHT -> {
                    vibrator.vibrate(VibrationEffect.createOneShot(20L, 50))
                }
                HapticStyle.MEDIUM -> {
                    vibrator.vibrate(VibrationEffect.createOneShot(45L, 130))
                }
                HapticStyle.HEAVY -> {
                    vibrator.vibrate(VibrationEffect.createOneShot(85L, 255))
                }
                HapticStyle.HEARTBEAT_CALM -> {
                    // Double pulse (lub-dub) subtle heartbeat
                    val timings = longArrayOf(0L, 20L, 70L, 25L)
                    val amplitudes = intArrayOf(0, 50, 0, 75)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                HapticStyle.HEARTBEAT_URGENT -> {
                    // Urgent thumping double pulse
                    val timings = longArrayOf(0L, 28L, 50L, 38L)
                    val amplitudes = intArrayOf(0, 130, 0, 190)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                HapticStyle.ADRENALINE_SNAP -> {
                    // Sharp double-tick
                    val timings = longArrayOf(0L, 15L, 25L, 20L)
                    val amplitudes = intArrayOf(0, 240, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                HapticStyle.CONCUSSION_RUMBLE -> {
                    // Heavy resonant shudder
                    val timings = longArrayOf(0L, 60L, 30L, 80L)
                    val amplitudes = intArrayOf(0, 200, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                HapticStyle.DASH_BURST -> {
                    // Swift tactile snap
                    vibrator.vibrate(VibrationEffect.createOneShot(32L, 180))
                }
                HapticStyle.STREAK_FIRE -> {
                    // Rapid 3-pulse sizzling excitement pattern
                    val timings = longArrayOf(0L, 25L, 35L, 30L, 35L, 40L)
                    val amplitudes = intArrayOf(0, 160, 0, 210, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val duration = when (style) {
                HapticStyle.LIGHT -> 20L
                HapticStyle.MEDIUM -> 40L
                HapticStyle.HEAVY, HapticStyle.CONCUSSION_RUMBLE -> 80L
                HapticStyle.HEARTBEAT_CALM -> 25L
                HapticStyle.HEARTBEAT_URGENT, HapticStyle.ADRENALINE_SNAP, HapticStyle.DASH_BURST, HapticStyle.STREAK_FIRE -> 45L
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
