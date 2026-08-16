package com.sbro.emucorec.ui.settings

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sbro.emucorec.core.Ps3CoreConfigRepository
import com.sbro.emucorec.data.CustomizationPreferences
import com.sbro.emucorec.ui.emulation.OnScreenControls
import com.sbro.emucorec.ui.emulation.TouchControlLayoutRepository

/**
 * Standalone touch control layout editor.
 *
 * With a null/blank titleId it edits the global default layout. With a titleId
 * it edits that game's custom layout. Per-game layouts are never overwritten
 * by global edits: they are loaded in place of the global one while playing.
 */
@Composable
fun TouchControlsEditorScreen(
    onExit: () -> Unit,
    titleId: String? = null
) {
    val context = LocalContext.current
    val configRepository = remember(context) { Ps3CoreConfigRepository(context) }
    val config = remember(configRepository) { configRepository.load() }
    val customizationPreferences = remember(context) { CustomizationPreferences(context) }
    val customization by customizationPreferences.settings.collectAsState()
    val layoutRepository = remember(context) { TouchControlLayoutRepository(context) }
    var layout by remember(titleId) { mutableStateOf(layoutRepository.load(titleId)) }

    DisposableEffect(customizationPreferences) {
        onDispose { customizationPreferences.close() }
    }

    // The touch controls only exist in landscape (the game is landscape-only);
    // lock the editor to landscape and release the lock when leaving it.
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        OnScreenControls(
            overlayScale = config.overlayScale,
            overlayOpacity = config.overlayOpacity,
            visualStyle = customization.touchControlVisualStyle,
            pressEffect = customization.touchControlPressEffect,
            touchHaptics = false,
            touchHapticsPreset = config.touchHapticsPreset,
            touchHapticsStrength = config.touchHapticsStrength,
            editMode = true,
            savedLayout = layout,
            onLayoutChange = { updated ->
                layout = updated
                layoutRepository.save(titleId, updated)
            },
            onEditDone = onExit,
            onEditReset = {
                layoutRepository.reset(titleId)
                layout = null
            },
            onButtonChange = { _, _ -> },
            onAxisChange = { _, _ -> }
        )
    }
}
