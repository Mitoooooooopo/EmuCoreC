package com.sbro.emucorec.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSurfaceAuditContractTest {
    @Test
    fun dataScreensAreReachableAndRefreshWhenReturning() {
        val refreshableScreens = listOf(
            "ui/playtime/PlayTimeScreen.kt",
            "ui/achievements/AchievementsScreen.kt",
            "ui/saves/SaveDataScreen.kt",
            "ui/profile/ProfileScreen.kt",
        )

        refreshableScreens.forEach { path ->
            val screen = source(path)
            assertTrue("$path must observe lifecycle resume", "Lifecycle.Event.ON_RESUME" in screen)
            assertTrue("$path must refresh its data", "viewModel.refresh" in screen)
        }
    }

    @Test
    fun emulationAndPlayTimeReflectSuccessfulActiveEmulation() {
        val app = source("EmuCoreCApp.kt")
        val emulator = source("core/ps3/Emulator.kt")
        val onCreate = emulator.substringAfter("override fun onCreate").substringBefore("override fun onNewIntent")

        assertTrue("A successful core boot must enable play-time tracking", "bootSucceeded = true" in emulator)
        assertTrue("Background time must close and accumulate the active segment", "finishPlayTimeSessionIfNeeded(accumulate = true)" in emulator)
        assertFalse("A session must not start before the core boots", "startPlayTimeSessionIfNeeded()" in onCreate)
        assertTrue("Crashes must not leave an endless open session", "finishOpenSessions()" in app)
    }

    private fun source(relative: String): String = sourceRoot().resolve(relative).readText()

    private fun sourceRoot(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val appModule = sequenceOf(workingDirectory, workingDirectory.resolve("app"))
            .firstOrNull { Files.isDirectory(it.resolve("src/main")) }
            ?: error("Unable to locate Android app module from $workingDirectory")
        return appModule.resolve("src/main/java/com/sbro/emucorec")
    }
}
