package com.sbro.emucorec.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveContentInstallerTest {
    @Test
    fun `natural ordering keeps numbered parts in numeric order`() {
        val names = listOf("game_part10.pkg", "game_part2.pkg", "game_part1.pkg")
            .sortedWith(ArchiveContentInstaller::naturalCompare)

        assertEquals(listOf("game_part1.pkg", "game_part2.pkg", "game_part10.pkg"), names)
    }

    @Test
    fun `picker accepts direct content and common multipart archive names`() {
        listOf(
            "game.pkg",
            "disc.iso",
            "bundle.zip",
            "bundle.z01",
            "bundle.rar",
            "bundle.part02.rar",
            "bundle.r00",
            "game.pkg.66601",
        ).forEach { name ->
            assertTrue(name, ArchiveContentInstaller.isSupportedSelectionName(name))
        }
        assertFalse(ArchiveContentInstaller.isSupportedSelectionName("readme.txt"))
    }

    @Test
    fun `zip content is extracted and discovered`() = withTempDirectory { root ->
        val archive = File(root, "game.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("release/game.pkg"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }

        val prepared = ArchiveContentInstaller.prepareInRoot(root, listOf(archive))

        assertEquals(1, prepared.files.size)
        assertEquals("game.pkg", prepared.files.single().name)
        assertTrue(prepared.files.single().readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        prepared.temporaryRoot?.deleteRecursively()
    }

    @Test
    fun `zip traversal is rejected before writing outside extraction folder`() = withTempDirectory { root ->
        val archive = File(root, "unsafe.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escaped.pkg"))
            zip.write(byteArrayOf(9))
            zip.closeEntry()
        }

        try {
            ArchiveContentInstaller.prepareInRoot(root, listOf(archive))
            fail("Unsafe archive must be rejected")
        } catch (error: ArchivePreparationException) {
            assertEquals(ArchivePreparationError.UnsafeEntry, error.reason)
        }
        assertFalse(File(root, "escaped.pkg").exists())
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val root = kotlin.io.path.createTempDirectory("emucorec-archive-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
