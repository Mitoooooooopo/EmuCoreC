package com.sbro.emucorec.core

import android.content.Context
import com.github.junrar.Archive
import com.github.junrar.exception.MissingNextVolumeException
import net.lingala.zip4j.ZipFile
import java.io.File
import java.nio.file.Files
import java.util.UUID

enum class ArchivePreparationError {
    MissingVolume,
    PasswordProtected,
    UnsafeEntry,
    NotEnoughSpace,
    NoInstallableContent,
    ExtractionFailed,
}

class ArchivePreparationException(
    val reason: ArchivePreparationError,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

data class PreparedPs3Content(
    val files: List<File>,
    val temporaryRoot: File?,
)

/** Safely expands ZIP/RAR selections and returns only content understood by RPCS3. */
object ArchiveContentInstaller {
    private const val MAX_ARCHIVE_ENTRIES = 20_000
    private const val STORAGE_HEADROOM = 512L * 1024L * 1024L
    private val installableExtensions = setOf("pkg", "iso", "rap", "edat")
    private val numberedPkgPart = Regex(".*\\.pkg\\.\\d+$", RegexOption.IGNORE_CASE)
    private val zipPart = Regex(".*\\.z\\d{2,}$", RegexOption.IGNORE_CASE)
    private val oldRarPart = Regex(".*\\.r\\d{2,}$", RegexOption.IGNORE_CASE)
    private val numberedRar = Regex("^(.*\\.part)(\\d+)(\\.rar)$", RegexOption.IGNORE_CASE)

    fun prepare(
        context: Context,
        selectedFiles: List<File>,
        onProgress: (String) -> Unit = {},
    ): PreparedPs3Content = prepareInRoot(
        stagingRoot = EmulatorStorage.installStagingRoot(context),
        selectedFiles = selectedFiles,
        onProgress = onProgress,
    )

    internal fun prepareInRoot(
        stagingRoot: File,
        selectedFiles: List<File>,
        onProgress: (String) -> Unit = {},
    ): PreparedPs3Content {
        if (selectedFiles.isEmpty()) {
            throw ArchivePreparationException(ArchivePreparationError.NoInstallableContent)
        }

        val inputs = selectedFiles.filter(File::isFile).distinctBy { it.canonicalPath }
        val direct = inputs.filter(::isInstallableContentFile)
        val zipRoots = inputs.filter { it.extension.equals("zip", ignoreCase = true) }
        val rarRoots = inputs.filter(::isFirstRarVolume)
        if (hasMissingArchiveVolume(inputs)) {
            throw ArchivePreparationException(ArchivePreparationError.MissingVolume)
        }

        if (zipRoots.isEmpty() && rarRoots.isEmpty()) {
            if (direct.isEmpty()) {
                throw ArchivePreparationException(ArchivePreparationError.NoInstallableContent)
            }
            return PreparedPs3Content(direct.sortedWith(naturalFileOrder), null)
        }

        val sessionRoot = File(stagingRoot, "archive-${UUID.randomUUID()}").apply { mkdirs() }
        val extractedRoots = mutableListOf<File>()

        try {
            (zipRoots.sortedWith(naturalFileOrder) + rarRoots.sortedWith(naturalFileOrder))
                .forEachIndexed { index, archive ->
                    val destination = File(sessionRoot, "${index}-${safeStem(archive.nameWithoutExtension)}")
                        .apply { mkdirs() }
                    onProgress(archive.name)
                    when {
                        archive.extension.equals("zip", ignoreCase = true) -> extractZip(archive, destination)
                        archive.extension.equals("rar", ignoreCase = true) -> extractRar(archive, destination)
                    }
                    validateExtractedTree(destination)
                    extractedRoots += destination
                }

            val extracted = extractedRoots
                .asSequence()
                .flatMap { it.walkTopDown().asSequence() }
                .filter(File::isFile)
                .filter(::isInstallableContentFile)
                .toList()
            val result = (direct + extracted)
                .distinctBy { it.canonicalPath }
                .sortedWith(naturalFileOrder)
            if (result.isEmpty()) {
                throw ArchivePreparationException(ArchivePreparationError.NoInstallableContent)
            }
            return PreparedPs3Content(result, sessionRoot)
        } catch (error: ArchivePreparationException) {
            sessionRoot.deleteRecursively()
            throw error
        } catch (error: MissingNextVolumeException) {
            sessionRoot.deleteRecursively()
            throw ArchivePreparationException(ArchivePreparationError.MissingVolume, error)
        } catch (error: Throwable) {
            sessionRoot.deleteRecursively()
            throw ArchivePreparationException(ArchivePreparationError.ExtractionFailed, error)
        }
    }

    private fun extractZip(archive: File, destination: File) {
        val zip = ZipFile(archive)
        if (!zip.isValidZipFile) {
            throw ArchivePreparationException(ArchivePreparationError.ExtractionFailed)
        }
        if (zip.isEncrypted) {
            throw ArchivePreparationException(ArchivePreparationError.PasswordProtected)
        }
        val headers = zip.fileHeaders
        if (headers.size > MAX_ARCHIVE_ENTRIES) {
            throw ArchivePreparationException(ArchivePreparationError.NotEnoughSpace)
        }
        headers.forEach { header -> validateEntry(destination, header.fileName) }
        checkSpace(destination, headers.sumOf { it.uncompressedSize.coerceAtLeast(0L) })
        headers.forEach { header ->
            val target = safeTarget(destination, header.fileName)
            if (header.isDirectory) {
                if (!target.mkdirs() && !target.isDirectory) {
                    throw ArchivePreparationException(ArchivePreparationError.ExtractionFailed)
                }
            } else {
                if (!target.parentFile.orEmptyDirectory()) {
                    throw ArchivePreparationException(ArchivePreparationError.ExtractionFailed)
                }
                zip.getInputStream(header).use { input ->
                    target.outputStream().buffered().use(input::copyTo)
                }
            }
        }
    }

    private fun extractRar(archive: File, destination: File) {
        Archive(archive).use { rar ->
            if (rar.isEncrypted || rar.isPasswordProtected) {
                throw ArchivePreparationException(ArchivePreparationError.PasswordProtected)
            }
            val headers = rar.fileHeaders
            if (headers.size > MAX_ARCHIVE_ENTRIES) {
                throw ArchivePreparationException(ArchivePreparationError.NotEnoughSpace)
            }
            headers.forEach { header ->
                validateEntry(destination, header.fileName)
                if (header.redirection != null) {
                    throw ArchivePreparationException(ArchivePreparationError.UnsafeEntry)
                }
            }
            checkSpace(destination, headers.sumOf { it.fullUnpackSize.coerceAtLeast(0L) })
            headers.forEach { header ->
                val target = safeTarget(destination, header.fileName)
                if (header.isDirectory) {
                    if (!target.mkdirs() && !target.isDirectory) {
                        throw ArchivePreparationException(ArchivePreparationError.ExtractionFailed)
                    }
                } else {
                    if (!target.parentFile.orEmptyDirectory()) {
                        throw ArchivePreparationException(ArchivePreparationError.ExtractionFailed)
                    }
                    target.outputStream().buffered().use { output -> rar.extractFile(header, output) }
                }
            }
        }
    }

    private fun validateEntry(destination: File, entryName: String) {
        if (entryName.isBlank() || entryName.indexOf('\u0000') >= 0) {
            throw ArchivePreparationException(ArchivePreparationError.UnsafeEntry)
        }
        val root = destination.canonicalFile
        val candidate = File(root, entryName.replace('\\', '/')).canonicalFile
        if (candidate != root && !candidate.path.startsWith(root.path + File.separator)) {
            throw ArchivePreparationException(ArchivePreparationError.UnsafeEntry)
        }
    }

    private fun safeTarget(destination: File, entryName: String): File {
        validateEntry(destination, entryName)
        return File(destination.canonicalFile, entryName.replace('\\', '/')).canonicalFile
    }

    private fun File?.orEmptyDirectory(): Boolean {
        val directory = this ?: return false
        return directory.isDirectory || directory.mkdirs()
    }

    private fun validateExtractedTree(destination: File) {
        val root = destination.canonicalFile
        var count = 0
        var bytes = 0L
        destination.walkTopDown().forEach { file ->
            count++
            if (count > MAX_ARCHIVE_ENTRIES + 1 || Files.isSymbolicLink(file.toPath())) {
                throw ArchivePreparationException(ArchivePreparationError.UnsafeEntry)
            }
            val canonical = file.canonicalFile
            if (canonical != root && !canonical.path.startsWith(root.path + File.separator)) {
                throw ArchivePreparationException(ArchivePreparationError.UnsafeEntry)
            }
            if (file.isFile) {
                bytes = safeAdd(bytes, file.length())
                checkSpace(destination, bytes)
            }
        }
    }

    private fun checkSpace(destination: File, unpackedBytes: Long) {
        val required = runCatching {
            Math.addExact(Math.multiplyExact(unpackedBytes.coerceAtLeast(0L), 2L), STORAGE_HEADROOM)
        }.getOrElse { Long.MAX_VALUE }
        if (required > destination.usableSpace) {
            throw ArchivePreparationException(ArchivePreparationError.NotEnoughSpace)
        }
    }

    private fun safeAdd(first: Long, second: Long): Long =
        runCatching { Math.addExact(first, second) }.getOrElse { Long.MAX_VALUE }

    private fun isFirstRarVolume(file: File): Boolean {
        if (!file.extension.equals("rar", ignoreCase = true)) return false
        val numbered = numberedRar.matchEntire(file.name) ?: return true
        return numbered.groupValues[2].toIntOrNull() == 1
    }

    private fun hasMissingArchiveVolume(files: List<File>): Boolean {
        val names = files.map { it.name.lowercase() }.toSet()

        files.filter { zipPart.matches(it.name) }
            .groupBy { it.name.lowercase().substringBeforeLast('.') }
            .forEach { (base, parts) ->
                if ("$base.zip" !in names) return true
                val numbers = parts.mapNotNull { it.extension.drop(1).toIntOrNull() }.toSet()
                if (numbers.isNotEmpty() && (1..numbers.max()).any { it !in numbers }) return true
            }

        files.filter { oldRarPart.matches(it.name) }
            .groupBy { it.name.lowercase().substringBeforeLast('.') }
            .forEach { (base, parts) ->
                if ("$base.rar" !in names) return true
                val numbers = parts.mapNotNull { it.extension.drop(1).toIntOrNull() }.toSet()
                if (numbers.isNotEmpty() && (0..numbers.max()).any { it !in numbers }) return true
            }

        files.mapNotNull { file -> numberedRar.matchEntire(file.name)?.let { file to it } }
            .groupBy { (_, match) -> (match.groupValues[1] + match.groupValues[3]).lowercase() }
            .forEach { (_, parts) ->
                val numbers = parts.mapNotNull { (_, match) -> match.groupValues[2].toIntOrNull() }.toSet()
                if (1 !in numbers || (1..numbers.max()).any { it !in numbers }) return true
            }
        return false
    }

    fun isSupportedSelectionName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.substringAfterLast('.', "") in setOf("pkg", "iso", "rap", "edat", "zip", "rar") ||
            zipPart.matches(name) || oldRarPart.matches(name) || numberedPkgPart.matches(name)
    }

    fun isInstallableContentFile(file: File): Boolean =
        file.extension.lowercase() in installableExtensions || numberedPkgPart.matches(file.name)

    private fun safeStem(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-', '.')
        .take(80)
        .ifBlank { "content" }

    internal val naturalFileOrder = Comparator<File> { first, second ->
        naturalCompare(first.name, second.name)
    }

    internal fun naturalCompare(first: String, second: String): Int {
        var i = 0
        var j = 0
        while (i < first.length && j < second.length) {
            val a = first[i]
            val b = second[j]
            if (a.isDigit() && b.isDigit()) {
                var ai = i
                while (ai < first.length && first[ai].isDigit()) ai++
                var bj = j
                while (bj < second.length && second[bj].isDigit()) bj++
                val left = first.substring(i, ai).trimStart('0')
                val right = second.substring(j, bj).trimStart('0')
                if (left.length != right.length) return left.length - right.length
                val order = left.compareTo(right)
                if (order != 0) return order
                i = ai
                j = bj
            } else {
                val order = a.lowercaseChar().compareTo(b.lowercaseChar())
                if (order != 0) return order
                i++
                j++
            }
        }
        return (first.length - i) - (second.length - j)
    }
}
