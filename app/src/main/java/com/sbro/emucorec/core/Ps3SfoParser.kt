package com.sbro.emucorec.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Ps3SfoData(
    val titleId: String? = null,
    val title: String? = null,
    val version: String? = null,
    val category: String? = null,
    val contentId: String? = null,
    val saveDataId: String? = null
)

object Ps3SfoParser {
    fun parse(file: File): Ps3SfoData {
        if (!file.exists() || !file.isFile) return Ps3SfoData()
        val bytes = runCatching { file.readBytes() }.getOrElse { return Ps3SfoData() }
        return parse(bytes)
    }

    fun parse(bytes: ByteArray): Ps3SfoData {
        // A malformed file must never take the library scan down: offsets and
        // counts in the header are attacker/garbage-controlled, and the
        // ByteBuffer accessors below throw BufferUnderflowException when they
        // run past the end. Return an empty result instead.
        return runCatching {
            parseUnsafe(bytes)
        }.getOrDefault(Ps3SfoData())
    }

    private fun parseUnsafe(bytes: ByteArray): Ps3SfoData {
        if (bytes.size < 20) return Ps3SfoData()

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != 0x46535000) return Ps3SfoData()

        buffer.short
        buffer.short
        val keyTableStart = buffer.int
        val dataTableStart = buffer.int
        val entryCount = buffer.int
        val values = mutableMapOf<String, String>()

        repeat(entryCount) {
            val keyOffset = buffer.short.toInt() and 0xFFFF
            buffer.get()
            buffer.get()
            val valueLength = buffer.int
            buffer.int
            val dataOffset = buffer.int
            val key = readCString(bytes, keyTableStart + keyOffset)
            val valueStart = dataTableStart + dataOffset
            if (valueStart !in bytes.indices) return@repeat
            val valueEnd = (valueStart + valueLength).coerceAtMost(bytes.size)
            val value = bytes.copyOfRange(valueStart, valueEnd).decodeToString()
                .replace("\u0000", "")
                .trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                values[key] = value
            }
        }

        return Ps3SfoData(
            titleId = values["TITLE_ID"],
            title = values["TITLE"],
            version = values["APP_VER"] ?: values["VERSION"] ?: "01.00",
            category = values["CATEGORY"],
            contentId = values["CONTENT_ID"],
            saveDataId = values["INSTALL_DIR_SAVEDATA"] ?: values["TITLE_ID"]
        )
    }

    private fun readCString(bytes: ByteArray, start: Int): String {
        if (start !in bytes.indices) return ""
        var end = start
        while (end < bytes.size && bytes[end].toInt() != 0) {
            end++
        }
        return bytes.copyOfRange(start, end).decodeToString()
    }
}
