package com.sbro.emucorec.ui.settings

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandingUiContractTest {
    @Test
    fun drawerUsesTheSingleEmuCoreCBrandAsset() {
        val source = appModuleRoot().resolve("src/main/java/com/sbro/emucorec/navigation/AdaptiveShell.kt").readText()
        assertTrue("Drawer must use the EmuCoreC icon", "R.drawable.ic_drawer_app" in source)
        assertFalse("Removed Pro branding must not return", "ic_drawer_app_pro" in source || "isProUnlocked" in source)
    }

    @Test
    fun drawerPngIsAProductionSizeSquareAsset() {
        val icon = appModuleRoot().resolve("src/main/res/drawable-nodpi/ic_drawer_app.png")
        assertEquals(512 to 512, pngDimensions(icon))
    }

    @Test
    fun adaptiveLauncherContainsTheOpenC() {
        val vector = appModuleRoot().resolve("src/main/res/drawable/ic_launcher_foreground.xml").readText()
        assertTrue("Launcher must contain the three-part open C", vector.split("<path").size >= 3)
        assertFalse("Old emulator product names must not leak into branding", "EmuCoreV" in vector || "EmuCoreX" in vector)
        assertFalse("Removed PS3 numeral must not return", "Small PS3 numeral" in vector)
    }

    @Test
    fun iconGeneratorHasAReproducibleCheckModeForEmuCoreC() {
        val root = appModuleRoot().parent
        val scriptPath = sequenceOf(
            root.resolve("tools/generate_drawer_icons.py"),
            root.resolve("emucorec-tools/generate_drawer_icons.py"),
            root.resolve("EmuCoreC(files)/tools/generate_drawer_icons.py")
        ).firstOrNull { Files.isRegularFile(it) } ?: error("generate_drawer_icons.py not found")
        val script = scriptPath.readText()
        assertTrue("--check" in script)
        assertTrue("ic_drawer_app.png" in script)
        assertFalse("Removed Pro icon must not be generated", "ic_drawer_app_pro.png" in script)
    }

    private fun pngDimensions(path: Path): Pair<Int, Int> {
        val header = path.readBytes().take(24).toByteArray()
        assertTrue("$path must be a PNG", header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE))
        val dimensions = ByteBuffer.wrap(header, 16, 8).order(ByteOrder.BIG_ENDIAN)
        return dimensions.int to dimensions.int
    }

    private fun appModuleRoot(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        return sequenceOf(workingDirectory, workingDirectory.resolve("app"))
            .firstOrNull { Files.isDirectory(it.resolve("src/main")) }
            ?: error("Unable to locate Android app module from $workingDirectory")
    }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
