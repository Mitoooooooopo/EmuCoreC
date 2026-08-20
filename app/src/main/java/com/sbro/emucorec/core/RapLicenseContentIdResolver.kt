package com.sbro.emucorec.core

import com.sbro.emucorec.data.InstalledPs3Game
import java.io.File
import java.nio.charset.StandardCharsets

/** Resolves the NPDRM content id that RPCS3 requires as a RAP filename. */
internal object RapLicenseContentIdResolver {
    private const val CONTENT_ID_LENGTH = 36
    private const val SCAN_CHUNK_SIZE = 64 * 1024
    private const val MAX_SCAN_BYTES = 16L * 1024L * 1024L
    private val contentIdRegex = Regex("[A-Z]{2}[0-9]{4}-[A-Z0-9]{9}_[0-9]{2}-[A-Z0-9]{16}")

    fun resolve(rapFile: File, installedGames: List<InstalledPs3Game>): String? {
        canonicalContentId(rapFile.nameWithoutExtension)?.let { return it }

        val rapLabel = normalizeLabel(rapFile.nameWithoutExtension)
        if (rapLabel.isBlank()) return null

        val matchingGames = installedGames.filter { game ->
            val title = normalizeLabel(game.title)
            val titleId = normalizeLabel(game.titleId)
            title == rapLabel || (titleId.isNotBlank() && rapLabel.contains(titleId))
        }
        if (matchingGames.isEmpty()) return null

        val resolved = matchingGames.flatMap { game ->
            val metadataId = canonicalContentId(game.contentId)
            val ebootIds = findEboot(game.installPath)
                ?.let(::readContentIds)
                .orEmpty()
            (listOfNotNull(metadataId) + ebootIds)
                .filter { contentId -> contentId.contains(game.titleId.trim().uppercase()) }
        }.distinct()

        return resolved.singleOrNull()
    }

    internal fun canonicalContentId(value: String?): String? {
        val normalized = value?.trim()?.uppercase().orEmpty()
        return normalized.takeIf(contentIdRegex::matches)
    }

    internal fun readContentIds(file: File): List<String> {
        if (!file.isFile) return emptyList()
        val found = linkedSetOf<String>()
        runCatching {
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(SCAN_CHUNK_SIZE)
                var tail = ByteArray(0)
                var scanned = 0L
                while (scanned < MAX_SCAN_BYTES) {
                    val requested = minOf(buffer.size.toLong(), MAX_SCAN_BYTES - scanned).toInt()
                    val count = input.read(buffer, 0, requested)
                    if (count <= 0) break
                    scanned += count

                    val bytes = ByteArray(tail.size + count)
                    tail.copyInto(bytes)
                    buffer.copyInto(bytes, tail.size, 0, count)
                    val text = String(bytes, StandardCharsets.US_ASCII)
                    contentIdRegex.findAll(text).forEach { found += it.value }

                    val overlap = minOf(CONTENT_ID_LENGTH - 1, bytes.size)
                    tail = bytes.copyOfRange(bytes.size - overlap, bytes.size)
                }
            }
        }
        return found.toList()
    }

    private fun findEboot(installPath: String): File? {
        val root = File(installPath)
        if (!root.isDirectory) return null
        return listOf(
            File(root, "USRDIR/EBOOT.BIN"),
            File(root, "EBOOT.BIN"),
            File(root, "PS3_GAME/USRDIR/EBOOT.BIN"),
            File(root, "PS3_GAME/EBOOT.BIN"),
        ).firstOrNull(File::isFile)
    }

    private fun normalizeLabel(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)
}
