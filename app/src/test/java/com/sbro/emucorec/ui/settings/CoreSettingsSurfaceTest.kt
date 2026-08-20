package com.sbro.emucorec.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSettingsSurfaceTest {
    @Test
    fun inGameSurfaceContainsOnlySafeLiveControls() {
        assertTrue(isCoreSettingVisibleOnSurface("Video@@Resolution Scale", Ps3CoreSettingsSurface.InGame))
        assertTrue(isCoreSettingVisibleOnSurface("Audio@@Master Volume", Ps3CoreSettingsSurface.InGame))
        assertFalse(isCoreSettingVisibleOnSurface("System@@Language", Ps3CoreSettingsSurface.InGame))
        assertFalse(isCoreSettingVisibleOnSurface("System@@Keyboard Type", Ps3CoreSettingsSurface.InGame))
        assertFalse(isCoreSettingVisibleOnSurface("Net@@Network Status", Ps3CoreSettingsSurface.InGame))
        assertFalse(isCoreSettingVisibleOnSurface("VFS@@$(EmulatorDir)", Ps3CoreSettingsSurface.InGame))
    }

    @Test
    fun gameProfilesExcludeGlobalDevicesAndStorage() {
        assertTrue(isCoreSettingVisibleOnSurface("System@@Language", Ps3CoreSettingsSurface.GameProfile))
        assertTrue(isCoreSettingVisibleOnSurface("Video@@Write Color Buffers", Ps3CoreSettingsSurface.GameProfile))
        assertFalse(isCoreSettingVisibleOnSurface("Video@@Vulkan@@Adapter", Ps3CoreSettingsSurface.GameProfile))
        assertFalse(isCoreSettingVisibleOnSurface("Audio@@Audio Device", Ps3CoreSettingsSurface.GameProfile))
        assertFalse(isCoreSettingVisibleOnSurface("VFS@@$(EmulatorDir)", Ps3CoreSettingsSurface.GameProfile))
        assertTrue(isCoreSettingVisibleOnSurface("Net@@PSN status", Ps3CoreSettingsSurface.GameProfile))
        assertFalse(isCoreSettingVisibleOnSurface("Net@@DNS address", Ps3CoreSettingsSurface.GameProfile))
    }

    @Test
    fun compiledRenderersAreOfferedAndNullIsHidden() {
        assertTrue(
            userFacingCoreVariants("Video@@Renderer", listOf("Null", "OpenGL", "Vulkan")) ==
                listOf("OpenGL", "Vulkan")
        )
    }
}
