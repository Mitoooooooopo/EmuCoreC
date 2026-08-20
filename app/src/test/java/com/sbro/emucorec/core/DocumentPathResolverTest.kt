package com.sbro.emucorec.core

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPathResolverTest {
    @Test
    fun stagingNameCannotEscapeThroughProviderDisplayName() {
        assertEquals("payload.pkg", DocumentPathResolver.sanitizeStagingFileName("../../payload.pkg"))
        assertEquals("payload.pkg", DocumentPathResolver.sanitizeStagingFileName("..\\..\\payload.pkg"))
        assertEquals("install-content.bin", DocumentPathResolver.sanitizeStagingFileName("../.."))
        assertEquals("game part 01.pkg", DocumentPathResolver.sanitizeStagingFileName("game part 01.pkg"))
    }

    @Test
    fun rootContainmentDoesNotAcceptSiblingWithSamePrefix() {
        val parent = Files.createTempDirectory("emucorec-staging-test").toFile()
        try {
            val root = parent.resolve("install-staging").apply { mkdirs() }
            val child = root.resolve("session/game.pkg")
            val sibling = parent.resolve("install-staging-evil/game.pkg")

            assertTrue(DocumentPathResolver.isPathWithinRoot(root, child))
            assertTrue(DocumentPathResolver.isPathWithinRoot(root, root))
            assertFalse(DocumentPathResolver.isPathWithinRoot(root, sibling))
        } finally {
            parent.deleteRecursively()
        }
    }
}
