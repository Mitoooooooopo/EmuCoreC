package com.sbro.emucorec.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchListParserTest {
    @Test
    fun preservesSeparateRowsForEveryAppVersion() {
        val patches = PatchListParser.parse(
            """[
                {"hash":"abc","name":"60 FPS","appVersion":"01.00","game":"Demo","enabled":true},
                {"hash":"abc","name":"60 FPS","appVersion":"01.01","game":"Demo","enabled":false}
            ]""".trimIndent()
        )

        assertEquals(2, patches.size)
        assertEquals(listOf("01.00", "01.01"), patches.map { it.appVersion })
        assertTrue(patches.first().enabled)
        assertFalse(patches.last().enabled)
        assertEquals(2, patches.map { it.identityKey }.distinct().size)
    }

    @Test
    fun malformedRowsDoNotHideValidPatches() {
        val patches = PatchListParser.parse(
            """[
                null,
                {"name":"missing hash"},
                {"hash":"valid","name":"Patch","author":"Author"}
            ]""".trimIndent()
        )

        assertEquals(1, patches.size)
        assertEquals("valid", patches.single().hash)
        assertEquals("all", patches.single().appVersion)
    }

    @Test
    fun exactDuplicateRowsAreCollapsed() {
        val json = """[{"hash":"h","name":"n","appVersion":"All","game":"g"}]"""
        val patches = PatchListParser.parse("[${json.removePrefix("[").removeSuffix("]")},${json.removePrefix("[").removeSuffix("]")}]")

        assertEquals(1, patches.size)
    }
}
