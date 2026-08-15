package com.sbro.emucorec.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FolderGamesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun normalizeTrimsTrailingSlashes() {
        assertEquals("/storage/emulated/0/PS3Games", FolderGames.normalize("/storage/emulated/0/PS3Games/"))
        assertEquals("/storage/emulated/0/PS3Games", FolderGames.normalize("/storage/emulated/0/PS3Games///"))
        assertEquals("C:/Games/PS3", FolderGames.normalize("C:/Games/PS3/"))
    }

    @Test
    fun candidatePathsRerootsAndroidMountPoints() {
        val candidates = FolderGames.candidatePaths("/mnt/user/0/primary/PS3/BLES01234")
        assertTrue(candidates.contains("/mnt/user/0/primary/PS3/BLES01234"))
        assertTrue(candidates.contains("/storage/primary/PS3/BLES01234"))

        val mediaRwCandidates = FolderGames.candidatePaths("/mnt/media_rw/1234-5678/PS3/BLES01234")
        assertTrue(mediaRwCandidates.contains("/storage/1234-5678/PS3/BLES01234"))
    }

    @Test
    fun gameRootOfResolvesParentDirectoryProperly() {
        val rootDir = tempFolder.newFolder("MyGame")
        val directSfo = File(rootDir, "PARAM.SFO").apply { createNewFile() }
        assertEquals(FolderGames.normalize(rootDir.absolutePath), FolderGames.gameRootOf(directSfo.absolutePath))

        val ps3GameDir = File(rootDir, "PS3_GAME").apply { mkdirs() }
        val nestedSfo = File(ps3GameDir, "PARAM.SFO").apply { createNewFile() }
        assertEquals(FolderGames.normalize(rootDir.absolutePath), FolderGames.gameRootOf(nestedSfo.absolutePath))
    }

    @Test
    fun iconPathOfFindsDirectAndDiscIcons() {
        val rootDir = tempFolder.newFolder("IconGame")
        assertNull(FolderGames.iconPathOf(rootDir.absolutePath))

        val directIcon = File(rootDir, "ICON0.PNG").apply { createNewFile() }
        assertEquals(directIcon.absolutePath, FolderGames.iconPathOf(rootDir.absolutePath))

        directIcon.delete()
        val ps3GameDir = File(rootDir, "PS3_GAME").apply { mkdirs() }
        val discIcon = File(ps3GameDir, "ICON0.PNG").apply { createNewFile() }
        assertEquals(discIcon.absolutePath, FolderGames.iconPathOf(rootDir.absolutePath))
    }
}
