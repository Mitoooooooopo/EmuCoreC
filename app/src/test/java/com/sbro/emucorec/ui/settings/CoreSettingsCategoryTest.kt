package com.sbro.emucorec.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreSettingsCategoryTest {
    @Test
    fun everyRpcs3RootIsRoutedToAnEmuCoreTab() {
        val expected = mapOf(
            "System@@Language" to Ps3CoreSettingsCategory.General,
            "Miscellaneous@@Automatically start games after boot" to Ps3CoreSettingsCategory.General,
            "Video@@Renderer" to Ps3CoreSettingsCategory.Graphics,
            "Video@@Vulkan@@Adapter" to Ps3CoreSettingsCategory.Graphics,
            "Video@@Performance Overlay@@Enabled" to Ps3CoreSettingsCategory.Overlay,
            "Video@@Debug overlay" to Ps3CoreSettingsCategory.Overlay,
            "Video@@Shader Loading Dialog@@Blur effect strength" to Ps3CoreSettingsCategory.Overlay,
            "Audio@@Master Volume" to Ps3CoreSettingsCategory.Audio,
            "Input/Output@@Keyboard" to Ps3CoreSettingsCategory.Controls,
            "VFS@@Limit disk cache size" to Ps3CoreSettingsCategory.Storage,
            "Savestate@@Maximum SaveState Files" to Ps3CoreSettingsCategory.Storage,
            "Net@@Internet enabled" to Ps3CoreSettingsCategory.Network,
            "Core@@PPU Decoder" to Ps3CoreSettingsCategory.Advanced,
            "Log@@TTY" to Ps3CoreSettingsCategory.Advanced,
        )

        expected.forEach { (path, category) ->
            assertEquals(path, category, coreSettingsCategory(path))
        }
    }
}
