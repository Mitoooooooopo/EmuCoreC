package com.sbro.emucorec.core

import com.sbro.emucorec.data.InstalledPs3Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RapLicenseContentIdResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun contentIdFilenameIsAcceptedWithoutInstalledGameLookup() {
        val rap = temporaryFolder.newFile("ep9000-npea00256_00-godofwariihdeu00.rap")

        assertEquals(
            CONTENT_ID,
            RapLicenseContentIdResolver.resolve(rap, emptyList()),
        )
    }

    @Test
    fun descriptiveFilenameUsesMatchingInstalledGamesEbootContentId() {
        val rap = temporaryFolder.newFile("God of War II HD.rap")
        val gameRoot = temporaryFolder.newFolder("NPEA00256")
        val eboot = File(gameRoot, "USRDIR/EBOOT.BIN")
        eboot.parentFile!!.mkdirs()
        eboot.writeBytes(byteArrayOf(0, 1, 2) + CONTENT_ID.toByteArray() + byteArrayOf(0, 3))

        assertEquals(
            CONTENT_ID,
            RapLicenseContentIdResolver.resolve(rap, listOf(game(gameRoot))),
        )
    }

    @Test
    fun contentIdSplitAcrossReadChunksIsDetected() {
        val eboot = temporaryFolder.newFile("EBOOT.BIN")
        val prefix = ByteArray(64 * 1024 - 12) { 0 }
        eboot.writeBytes(prefix + CONTENT_ID.toByteArray())

        assertEquals(listOf(CONTENT_ID), RapLicenseContentIdResolver.readContentIds(eboot))
    }

    @Test
    fun unrelatedDescriptiveFilenameIsNotGuessed() {
        val rap = temporaryFolder.newFile("Unknown Game.rap")
        val gameRoot = temporaryFolder.newFolder("NPEA00256")

        assertNull(RapLicenseContentIdResolver.resolve(rap, listOf(game(gameRoot))))
    }

    @Test
    fun ambiguousMatchingContentIdsAreRejected() {
        val rap = temporaryFolder.newFile("God of War II HD.rap")
        val gameRoot = temporaryFolder.newFolder("NPEA00256")
        val eboot = File(gameRoot, "USRDIR/EBOOT.BIN")
        eboot.parentFile!!.mkdirs()
        eboot.writeText("$CONTENT_ID EP9000-NPEA00256_00-GODOFWARIIHDUS00")

        assertNull(RapLicenseContentIdResolver.resolve(rap, listOf(game(gameRoot))))
    }

    private fun game(root: File) = InstalledPs3Game(
        titleId = "NPEA00256",
        title = "God of War® II HD",
        contentId = null,
        saveDataId = "NPEA00256",
        version = "01.00",
        category = "HG",
        iconPath = null,
        catalogCoverUrl = null,
        installPath = root.absolutePath,
    )

    private companion object {
        const val CONTENT_ID = "EP9000-NPEA00256_00-GODOFWARIIHDEU00"
    }
}
