package com.sbro.emucorec.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * The library scan feeds every PARAM.SFO it finds to Ps3SfoParser. A corrupted
 * file must never take the app down, so parsing has to be total: any input
 * yields a Ps3SfoData, never an exception.
 */
class Ps3SfoParserTest {

    @Test
    fun emptyInputYieldsEmptyData() {
        assertEquals(Ps3SfoData(), Ps3SfoParser.parse(ByteArray(0)))
        assertEquals(Ps3SfoData(), Ps3SfoParser.parse(ByteArray(19)))
    }

    @Test
    fun nonPsfMagicYieldsEmptyData() {
        val garbage = ByteArray(64) { it.toByte() }
        assertEquals(Ps3SfoData(), Ps3SfoParser.parse(garbage))
    }

    @Test
    fun truncatedHeaderDoesNotThrow() {
        val header = psfHeader(entryCount = 100)
        // Cut the header short: entry table and data table are gone.
        val truncated = header.copyOf(16)
        assertEquals(Ps3SfoData(), Ps3SfoParser.parse(truncated))
    }

    @Test
    fun hugeEntryCountWithShortBodyDoesNotThrow() {
        val header = psfHeader(entryCount = 0x7FFF)
        // Header claims thousands of entries but there is no entry table.
        assertEquals(Ps3SfoData(), Ps3SfoParser.parse(header))
    }

    @Test
    fun outOfRangeEntryOffsetsDoNotThrow() {
        // entryCount = 2, key table at a huge offset, data table at a huge offset.
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46535000) // magic
        buffer.putShort(0x0001)
        buffer.putShort(0x0004)
        buffer.putInt(0xFFFFFF) // key table start
        buffer.putInt(0xFFFFFF) // data table start
        buffer.putInt(2) // entry count
        buffer.putShort(0)
        buffer.put(0)
        buffer.put(0)
        buffer.putInt(4)
        buffer.putInt(0)
        buffer.putInt(0xFFFFFF) // data offset
        buffer.putShort(0)
        buffer.put(0)
        buffer.put(0)
        buffer.putInt(4)
        buffer.putInt(0)
        buffer.putInt(0xFFFFFF)

        val result = Ps3SfoParser.parse(buffer.array())
        // Must not throw; out-of-range offsets simply yield no values.
        assertEquals(null, result.titleId)
        assertEquals(null, result.title)
    }

    @Test
    fun arbitraryBytesNeverThrow() {
        val random = Random(42)
        repeat(500) {
            val bytes = ByteArray(random.nextInt(0, 256)) { random.nextInt().toByte() }
            Ps3SfoParser.parse(bytes) // must not throw
        }
    }

    @Test
    fun validPsfParsesFields() {
        val sfo = buildPsf(
            mapOf(
                "TITLE_ID" to "BLES01421",
                "TITLE" to "Test Game",
                "APP_VER" to "01.02",
                "CATEGORY" to "DG",
                "CONTENT_ID" to "UP0001-BLES01421_00-0000000000000000"
            )
        )

        val parsed = Ps3SfoParser.parse(sfo)

        assertEquals("BLES01421", parsed.titleId)
        assertEquals("Test Game", parsed.title)
        assertEquals("01.02", parsed.version)
        assertEquals("DG", parsed.category)
        assertEquals("UP0001-BLES01421_00-0000000000000000", parsed.contentId)
    }

    private fun psfHeader(entryCount: Int): ByteArray {
        val buffer = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x46535000)
        buffer.putShort(0x0001)
        buffer.putShort(0x0004)
        buffer.putInt(20)
        buffer.putInt(20)
        buffer.putInt(entryCount)
        return buffer.array()
    }

    private fun buildPsf(entries: Map<String, String>): ByteArray {
        // PSF header + entry table + key table + data table.
        val keys = entries.keys.toList()
        val keyBytes = ByteArrayOutputStream()
        val dataBytes = ByteArrayOutputStream()

        val entryTable = ByteArrayOutputStream()
        for (key in keys) {
            val value = entries[key]!!
            val keyOffset = keyBytes.size()
            keyBytes.write(key.toByteArray(Charsets.UTF_8))
            keyBytes.write(0)

            val dataOffset = dataBytes.size()
            dataBytes.write(value.toByteArray(Charsets.UTF_8))
            dataBytes.write(0)

            val entry = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            entry.putShort(keyOffset.toShort())
            entry.put(0x04) // format: UTF-8
            entry.put(0)
            entry.putInt(value.length + 1)
            entry.putInt(value.length + 1)
            entry.putInt(dataOffset)
            entryTable.write(entry.array())
        }

        val keyTableStart = 20 + entryTable.size()
        val dataTableStart = keyTableStart + keyBytes.size()

        val header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x46535000)
        header.putShort(0x0001)
        header.putShort(0x0004)
        header.putInt(keyTableStart.toInt())
        header.putInt(dataTableStart.toInt())
        header.putInt(entries.size)

        val out = ByteArrayOutputStream()
        out.write(header.array())
        out.write(entryTable.toByteArray())
        out.write(keyBytes.toByteArray())
        out.write(dataBytes.toByteArray())
        return out.toByteArray()
    }
}
