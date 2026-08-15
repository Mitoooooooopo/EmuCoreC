package com.sbro.emucorec.core

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * EmuCoreC-owned Android UI and controller preferences.
 *
 * RPCS3/RPCS3 emulator settings intentionally do not live here: the app reads and writes
 * the complete native settings tree through RPCS3.settingsGet/settingsSet instead.
 */
data class Ps3CoreConfig(
    val customDriverName: String = "",
    val enableGamepadOverlay: Boolean = true,
    val overlayScale: Float = 0.9f,
    val overlayOpacity: Int = 100,
    val touchHaptics: Boolean = true,
    val touchHapticsPreset: Int = TOUCH_HAPTICS_PRESET_BALANCED,
    val touchHapticsStrength: Int = 60,
    val gyroMode: Int = GYRO_MODE_OFF,
    val gyroSensitivity: Int = 100,
    val gyroSmoothing: Int = 45,
    val gyroInvertX: Boolean = false,
    val gyroInvertY: Boolean = false,
    val analogMultiplier: Float = 1.0f,
    val gamepadDeadzone: Float = 0.15f,
    val gamepadTriggerThreshold: Float = 0.12f,
    val gamepadButtonProfile: String = GAMEPAD_PROFILE_STANDARD,
    val gamepadVibration: Boolean = true,
    val gamepadVibrationStrength: Int = 100,
    val deviceVibrationFallback: Boolean = true,
    val gamepadSwapSticks: Boolean = false,
    val gamepadInvertLeftX: Boolean = false,
    val gamepadInvertLeftY: Boolean = false,
    val gamepadInvertRightX: Boolean = false,
    val gamepadInvertRightY: Boolean = false,
) {
    companion object {
        const val GAMEPAD_PROFILE_STANDARD = "standard"
        const val GAMEPAD_PROFILE_SWAP_CROSS_CIRCLE = "swap-cross-circle"
        const val GAMEPAD_PROFILE_NINTENDO_FACE = "nintendo-face"

        const val TOUCH_HAPTICS_PRESET_SOFT = 0
        const val TOUCH_HAPTICS_PRESET_BALANCED = 1
        const val TOUCH_HAPTICS_PRESET_CRISP = 2
        const val TOUCH_HAPTICS_PRESET_STRONG = 3

        const val GYRO_MODE_OFF = 0
        const val GYRO_MODE_AIM = 1
        const val GYRO_MODE_STEERING = 2
    }
}

class Ps3CoreConfigRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Ps3CoreConfig {
        preferences.getString(KEY_CONFIG, null)?.let { raw ->
            runCatching { return JSONObject(raw).toPs3CoreConfig(Ps3CoreConfig()).normalized() }
        }
        return loadLegacyPreferences().normalized()
    }

    fun ensureDefaultsPersisted(): Ps3CoreConfig = load().also(::save)

    fun save(config: Ps3CoreConfig) {
        val normalized = config.normalized()
        synchronized(IO_LOCK) {
            preferences.edit(commit = true) {
                putString(KEY_CONFIG, normalized.toJsonObject().toString())
                putInt(KEY_SCHEMA, SCHEMA_VERSION)
            }
        }
    }

    fun resetToDefaults(): Ps3CoreConfig {
        val defaults = Ps3CoreConfig()
        synchronized(IO_LOCK) {
            preferences.edit(commit = true) {
                clear()
                putString(KEY_CONFIG, defaults.toJsonObject().toString())
                putInt(KEY_SCHEMA, SCHEMA_VERSION)
            }
        }
        return defaults
    }

    private fun loadLegacyPreferences(): Ps3CoreConfig = Ps3CoreConfig(
        customDriverName = preferences.getString("custom-driver-name", "").orEmpty(),
        enableGamepadOverlay = preferences.legacyBoolean("enable-gamepad-overlay", true),
        overlayScale = preferences.legacyFloat("overlay-scale", 0.9f),
        overlayOpacity = preferences.legacyInt("overlay-opacity", 100),
        touchHaptics = preferences.legacyBoolean("touch-haptics", true),
        touchHapticsPreset = preferences.legacyInt("touch-haptics-preset", Ps3CoreConfig.TOUCH_HAPTICS_PRESET_BALANCED),
        touchHapticsStrength = preferences.legacyInt("touch-haptics-strength", 60),
        gyroMode = preferences.legacyInt("gyro-mode", Ps3CoreConfig.GYRO_MODE_OFF),
        gyroSensitivity = preferences.legacyInt("gyro-sensitivity", 100),
        gyroSmoothing = preferences.legacyInt("gyro-smoothing", 45),
        gyroInvertX = preferences.legacyBoolean("gyro-invert-x", false),
        gyroInvertY = preferences.legacyBoolean("gyro-invert-y", false),
        analogMultiplier = preferences.legacyFloat("controller-analog-multiplier", 1f),
        gamepadDeadzone = preferences.legacyFloat("gamepad-deadzone", 0.15f),
        gamepadTriggerThreshold = preferences.legacyFloat("gamepad-trigger-threshold", 0.12f),
        gamepadButtonProfile = preferences.getString("gamepad-button-profile", Ps3CoreConfig.GAMEPAD_PROFILE_STANDARD)
            ?: Ps3CoreConfig.GAMEPAD_PROFILE_STANDARD,
        gamepadVibration = preferences.legacyBoolean("gamepad-vibration", true),
        gamepadVibrationStrength = preferences.legacyInt("gamepad-vibration-strength", 100),
        deviceVibrationFallback = preferences.legacyBoolean("device-vibration-fallback", true),
        gamepadSwapSticks = preferences.legacyBoolean("gamepad-swap-sticks", false),
        gamepadInvertLeftX = preferences.legacyBoolean("gamepad-invert-left-x", false),
        gamepadInvertLeftY = preferences.legacyBoolean("gamepad-invert-left-y", false),
        gamepadInvertRightX = preferences.legacyBoolean("gamepad-invert-right-x", false),
        gamepadInvertRightY = preferences.legacyBoolean("gamepad-invert-right-y", false),
    )

    private fun android.content.SharedPreferences.legacyBoolean(key: String, fallback: Boolean): Boolean =
        getString(key, null)?.toBooleanStrictOrNull() ?: fallback

    private fun android.content.SharedPreferences.legacyInt(key: String, fallback: Int): Int =
        getString(key, null)?.toIntOrNull() ?: fallback

    private fun android.content.SharedPreferences.legacyFloat(key: String, fallback: Float): Float =
        getString(key, null)?.toFloatOrNull() ?: fallback

    private companion object {
        val IO_LOCK = Any()
        const val PREFS_NAME = "emucorec_ui_config"
        const val KEY_CONFIG = "android_ui_config_v2"
        const val KEY_SCHEMA = "android_ui_schema"
        const val SCHEMA_VERSION = 3
    }
}

internal fun Ps3CoreConfig.normalized(): Ps3CoreConfig = copy(
    customDriverName = customDriverName.trim(),
    overlayScale = overlayScale.coerceIn(0.5f, 2f),
    overlayOpacity = overlayOpacity.coerceIn(10, 100),
    touchHapticsPreset = touchHapticsPreset.coerceIn(
        Ps3CoreConfig.TOUCH_HAPTICS_PRESET_SOFT,
        Ps3CoreConfig.TOUCH_HAPTICS_PRESET_STRONG,
    ),
    touchHapticsStrength = touchHapticsStrength.coerceIn(10, 100),
    gyroMode = gyroMode.coerceIn(Ps3CoreConfig.GYRO_MODE_OFF, Ps3CoreConfig.GYRO_MODE_STEERING),
    gyroSensitivity = gyroSensitivity.coerceIn(25, 300),
    gyroSmoothing = gyroSmoothing.coerceIn(0, 90),
    analogMultiplier = analogMultiplier.coerceIn(0.5f, 2f),
    gamepadDeadzone = gamepadDeadzone.coerceIn(0f, 0.45f),
    gamepadTriggerThreshold = gamepadTriggerThreshold.coerceIn(0f, 0.9f),
    gamepadButtonProfile = gamepadButtonProfile.takeIf {
        it in setOf(
            Ps3CoreConfig.GAMEPAD_PROFILE_STANDARD,
            Ps3CoreConfig.GAMEPAD_PROFILE_SWAP_CROSS_CIRCLE,
            Ps3CoreConfig.GAMEPAD_PROFILE_NINTENDO_FACE,
        )
    } ?: Ps3CoreConfig.GAMEPAD_PROFILE_STANDARD,
    gamepadVibrationStrength = gamepadVibrationStrength.coerceIn(0, 100),
)

internal fun Ps3CoreConfig.toJsonObject(): JSONObject = JSONObject()
    .put("customDriverName", customDriverName)
    .put("enableGamepadOverlay", enableGamepadOverlay)
    .put("overlayScale", overlayScale)
    .put("overlayOpacity", overlayOpacity)
    .put("touchHaptics", touchHaptics)
    .put("touchHapticsPreset", touchHapticsPreset)
    .put("touchHapticsStrength", touchHapticsStrength)
    .put("gyroMode", gyroMode)
    .put("gyroSensitivity", gyroSensitivity)
    .put("gyroSmoothing", gyroSmoothing)
    .put("gyroInvertX", gyroInvertX)
    .put("gyroInvertY", gyroInvertY)
    .put("analogMultiplier", analogMultiplier)
    .put("gamepadDeadzone", gamepadDeadzone)
    .put("gamepadTriggerThreshold", gamepadTriggerThreshold)
    .put("gamepadButtonProfile", gamepadButtonProfile)
    .put("gamepadVibration", gamepadVibration)
    .put("gamepadVibrationStrength", gamepadVibrationStrength)
    .put("deviceVibrationFallback", deviceVibrationFallback)
    .put("gamepadSwapSticks", gamepadSwapSticks)
    .put("gamepadInvertLeftX", gamepadInvertLeftX)
    .put("gamepadInvertLeftY", gamepadInvertLeftY)
    .put("gamepadInvertRightX", gamepadInvertRightX)
    .put("gamepadInvertRightY", gamepadInvertRightY)

internal fun JSONObject.toPs3CoreConfig(defaults: Ps3CoreConfig): Ps3CoreConfig = Ps3CoreConfig(
    customDriverName = optString("customDriverName", defaults.customDriverName),
    enableGamepadOverlay = optBoolean("enableGamepadOverlay", defaults.enableGamepadOverlay),
    overlayScale = optDouble("overlayScale", defaults.overlayScale.toDouble()).toFloat(),
    overlayOpacity = optInt("overlayOpacity", defaults.overlayOpacity),
    touchHaptics = optBoolean("touchHaptics", defaults.touchHaptics),
    touchHapticsPreset = optInt("touchHapticsPreset", defaults.touchHapticsPreset),
    touchHapticsStrength = optInt("touchHapticsStrength", defaults.touchHapticsStrength),
    gyroMode = optInt("gyroMode", defaults.gyroMode),
    gyroSensitivity = optInt("gyroSensitivity", defaults.gyroSensitivity),
    gyroSmoothing = optInt("gyroSmoothing", defaults.gyroSmoothing),
    gyroInvertX = optBoolean("gyroInvertX", defaults.gyroInvertX),
    gyroInvertY = optBoolean("gyroInvertY", defaults.gyroInvertY),
    analogMultiplier = optDouble("analogMultiplier", defaults.analogMultiplier.toDouble()).toFloat(),
    gamepadDeadzone = optDouble("gamepadDeadzone", defaults.gamepadDeadzone.toDouble()).toFloat(),
    gamepadTriggerThreshold = optDouble("gamepadTriggerThreshold", defaults.gamepadTriggerThreshold.toDouble()).toFloat(),
    gamepadButtonProfile = optString("gamepadButtonProfile", defaults.gamepadButtonProfile),
    gamepadVibration = optBoolean("gamepadVibration", defaults.gamepadVibration),
    gamepadVibrationStrength = optInt("gamepadVibrationStrength", defaults.gamepadVibrationStrength),
    deviceVibrationFallback = optBoolean("deviceVibrationFallback", defaults.deviceVibrationFallback),
    gamepadSwapSticks = optBoolean("gamepadSwapSticks", defaults.gamepadSwapSticks),
    gamepadInvertLeftX = optBoolean("gamepadInvertLeftX", defaults.gamepadInvertLeftX),
    gamepadInvertLeftY = optBoolean("gamepadInvertLeftY", defaults.gamepadInvertLeftY),
    gamepadInvertRightX = optBoolean("gamepadInvertRightX", defaults.gamepadInvertRightX),
    gamepadInvertRightY = optBoolean("gamepadInvertRightY", defaults.gamepadInvertRightY),
).normalized()
