package com.sbro.emucorec.data

import com.sbro.emucorec.core.Ps3SfoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InstalledGameRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parsesValidParamSfo() {
        val sfoFile = tempFolder.newFile("PARAM.SFO")
        sfoFile.writeBytes(createParamSfoBytes("BLES01234", "Demon's Souls"))

        val parsed = Ps3SfoParser.parse(sfoFile)
        assertEquals("BLES01234", parsed.titleId)
        assertEquals("Demon's Souls", parsed.title)
    }

    @Test
    fun locatesSfoAndIconInDiscStructure() {
        val gameFolder = tempFolder.newFolder("BLES00909")
        val ps3Game = File(gameFolder, "PS3_GAME").apply { mkdirs() }
        val sfoFile = File(ps3Game, "PARAM.SFO")
        val iconFile = File(ps3Game, "ICON0.PNG")
        sfoFile.writeBytes(createParamSfoBytes("BLES00909", "God of War III"))
        iconFile.writeBytes(byteArrayOf(1, 2, 3))

        val parsed = Ps3SfoParser.parse(sfoFile)
        assertEquals("BLES00909", parsed.titleId)
        assertEquals("God of War III", parsed.title)
        assertTrue(iconFile.isFile)
    }

    private fun createParamSfoBytes(titleId: String, title: String): ByteArray {
        val entries = listOf(
            "TITLE_ID" to titleId,
            "TITLE" to title
        )

        val headerSize = 20
        val entryTableSize = entries.size * 16
        val keyTableStart = headerSize + entryTableSize

        val keyBytes = mutableListOf<Byte>()
        val keyOffsets = mutableListOf<Int>()
        for ((key, _) in entries) {
            keyOffsets.add(keyBytes.size)
            keyBytes.addAll(key.toByteArray(Charsets.UTF_8).toList())
            keyBytes.add(0.toByte())
        }

        val dataTableStart = keyTableStart + keyBytes.size
        val dataBytes = mutableListOf<Byte>()
        val dataOffsets = mutableListOf<Int>()
        for ((_, value) in entries) {
            dataOffsets.add(dataBytes.size)
            dataBytes.addAll(value.toByteArray(Charsets.UTF_8).toList())
            dataBytes.add(0.toByte())
        }

        val totalSize = dataTableStart + dataBytes.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Magic 0x46535000 (\0PSF)
        buffer.putInt(0x46535000)
        buffer.putShort(0x0101) // version
        buffer.putShort(0x0000) // reserved
        buffer.putInt(keyTableStart)
        buffer.putInt(dataTableStart)
        buffer.putInt(entries.size)

        for (i in entries.indices) {
            buffer.putShort(keyOffsets[i].toShort())
            buffer.put(0x04.toByte()) // UTF-8 format
            buffer.put(0x04.toByte())
            val valLen = entries[i].second.toByteArray(Charsets.UTF_8).size + 1
            buffer.putInt(valLen)
            buffer.putInt(valLen)
            buffer.putInt(dataOffsets[i])
        }

        buffer.put(keyBytes.toByteArray())
        buffer.put(dataBytes.toByteArray())

        return buffer.array()
    }
}
