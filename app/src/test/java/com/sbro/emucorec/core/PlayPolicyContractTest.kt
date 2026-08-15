package com.sbro.emucorec.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPolicyContractTest {
    @Test
    fun productionManifestDoesNotRequestRestrictedInstallerPermissions() {
        val projectRoot = locateProjectRoot()
        val manifest = projectRoot.resolve("src/main/AndroidManifest.xml").readText()
        val forbidden = listOf(
            "REQUEST_INSTALL_PACKAGES"
        )

        forbidden.forEach { token ->
            assertFalse("Production manifest must not contain $token", token in manifest)
        }
        assertFalse("Removed Play Billing permission must not return", "com.android.vending.BILLING" in manifest)
    }

    @Test
    fun appUpdateFlowDoesNotDownloadOrInstallApks() {
        val sourceRoot = locateProjectRoot().resolve("src/main/java")
        val repository = sourceRoot
            .resolve("com/sbro/emucorec/core/AppUpdateRepository.kt")
            .readText()
        val navigation = sourceRoot
            .resolve("com/sbro/emucorec/navigation/AppNavigation.kt")
            .readText()

        assertFalse("Update repository must not launch package installers", "launchInstaller" in repository)
        assertFalse("Update repository must not download APK files", "downloadApk(" in repository)
        assertFalse("Startup update dialogs must stay removed", "AppUpdateAvailableDialog" in navigation)
        assertFalse("Startup update checks must stay removed", "checkForStartupAppUpdates" in navigation)
    }

    @Test
    fun privacyPolicyCardUsesPublishedEmuCoreCUrl() {
        val settingsSource = locateProjectRoot()
            .resolve("src/main/java/com/sbro/emucorec/ui/settings/SettingsTabContent.kt")
            .readText()
        assertTrue(
            "Privacy policy card must use the published Google Sites URL",
            "https://sites.google.com/view/privacy-policy-for-emucorec/" in settingsSource
        )
        assertTrue("Privacy policy card must be visible in About", "settings_about_privacy_policy" in settingsSource)
    }

    private fun locateProjectRoot(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        return sequenceOf(
            workingDirectory,
            workingDirectory.resolve("app")
        ).firstOrNull { Files.isDirectory(it.resolve("src/main")) }
            ?: error("Unable to locate Android app module from $workingDirectory")
    }
}
