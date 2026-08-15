package com.sbro.emucorec.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Ps3IsoParserTest {

    @Test
    fun isIsoImageDetectsValidExtensions() {
        assertTrue(Ps3IsoParser.isIsoImage(File("game.iso")))
        assertTrue(Ps3IsoParser.isIsoImage(File("game.ISO")))
        assertTrue(Ps3IsoParser.isIsoImage(File("game.bin")))
        assertTrue(Ps3IsoParser.isIsoImage(File("game.img")))
        assertTrue(Ps3IsoParser.isIsoImage(File("game.chd")))
        assertFalse(Ps3IsoParser.isIsoImage(File("game.pkg")))
        assertFalse(Ps3IsoParser.isIsoImage(File("game.rap")))
        assertFalse(Ps3IsoParser.isIsoImage(File("PARAM.SFO")))
    }

    @Test
    fun extractTitleIdFromFilenameExtractsSerial() {
        assertEquals("BCES00510", Ps3IsoParser.extractTitleIdFromFilename("[BCES00510] God of War III.iso"))
        assertEquals("BLUS30109", Ps3IsoParser.extractTitleIdFromFilename("LittleBigPlanet (USA) [BLUS30109].iso"))
        assertEquals("BLES01234", Ps3IsoParser.extractTitleIdFromFilename("BLES01234.iso"))
        assertEquals(null, Ps3IsoParser.extractTitleIdFromFilename("God of War III.iso"))
    }

    @Test
    fun cleanTitleFromFilenameCleansTagsAndSerials() {
        assertEquals("God of War III", Ps3IsoParser.cleanTitleFromFilename("[BCES00510] God of War III.iso"))
        assertEquals("LittleBigPlanet", Ps3IsoParser.cleanTitleFromFilename("LittleBigPlanet (USA) [BLUS30109].iso"))
        assertEquals("Demon's Souls", Ps3IsoParser.cleanTitleFromFilename("Demon's_Souls.iso"))
    }
}
