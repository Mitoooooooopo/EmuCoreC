package com.sbro.emucorec.core

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import android.util.Log
import java.util.UUID

object DocumentPathResolver {

    fun getDisplayName(context: Context, rawPath: String): String {
        if (rawPath.startsWith("file://")) return File(rawPath.toUri().path.orEmpty()).name
        if (!rawPath.startsWith("content://")) return File(rawPath).name
        val uri = rawPath.toUri()
        val directPath = resolveExternalStoragePath(uri)
        if (directPath != null) {
            val name = File(directPath).name
            if (name.isNotBlank()) return name
        }
        val docFile = runCatching {
            if (DocumentsContract.isTreeUri(uri)) {
                DocumentFile.fromTreeUri(context, uri)
            } else {
                DocumentFile.fromSingleUri(context, uri)
            }
        }.getOrNull()
        return docFile?.name
            ?: uri.lastPathSegment?.substringAfterLast(':')
            ?: rawPath.substringAfterLast('/')
    }

    fun resolveDirectoryPath(context: Context, rawPath: String): String? {
        if (rawPath.startsWith("file://")) return rawPath.toUri().path
        if (!rawPath.startsWith("content://")) return rawPath

        val uri = rawPath.toUri()
        val direct = resolveExternalStoragePath(uri)
        if (direct != null && (File(direct).exists() || File(direct).isDirectory)) {
            return direct
        }
        return direct
    }

    fun resolveFilePath(
        context: Context,
        rawPath: String,
        copyToCache: Boolean = false,
        stagingSession: File? = null,
    ): String? {
        if (rawPath.startsWith("file://")) return rawPath.toUri().path
        if (!rawPath.startsWith("content://")) return rawPath

        val uri = rawPath.toUri()
        val directPath = resolveExternalStoragePath(uri)
        if (directPath != null && (File(directPath).exists() || File(directPath).canRead())) {
            return directPath
        }

        val fileName = getDisplayName(context, rawPath)

        // Prefer resolving to a real on-disk path before falling back to a full
        // byte-for-byte copy. Install payloads are game-sized, so an avoidable
        // copy costs the user gigabytes of duplicated storage.
        val treePath = findFileInPersistedTree(context, uri, fileName)
        if (treePath != null) return treePath

        if (copyToCache) {
            return copyUriToCache(context, uri, fileName, stagingSession)
        }

        return null
    }

    /**
     * Deletes a staged install payload previously produced by [resolveFilePath].
     *
     * No-op when [path] is not inside the staging directory, so it is safe to
     * call with a directly-resolved source path that must not be removed.
     */
    fun cleanupStagedFile(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val staged = File(path)
            val stagingDir = EmulatorStorage.installStagingRoot(context)
            if (staged.isFile && isPathWithinRoot(stagingDir, staged)) {
                staged.delete()
            }
        }.onFailure { error ->
            Log.w("DocumentPathResolver", "Failed to clean staged install file: $path", error)
        }
    }

    fun createStagingSession(context: Context): File? = runCatching {
        val root = EmulatorStorage.installStagingRoot(context).canonicalFile
        if (!root.isDirectory && !root.mkdirs()) return null
        File(root, "source-${UUID.randomUUID()}").canonicalFile
            .takeIf { it.parentFile == root && (it.mkdirs() || it.isDirectory) }
    }.getOrNull()

    private fun copyUriToCache(
        context: Context,
        uri: Uri,
        fileName: String,
        stagingSession: File?,
    ): String? {
        var partialFile: File? = null
        return try {
            val stagingRoot = EmulatorStorage.installStagingRoot(context).canonicalFile
            val cacheDir = (stagingSession ?: createStagingSession(context) ?: return null).canonicalFile
            if (!isPathWithinRoot(stagingRoot, cacheDir) || (!cacheDir.isDirectory && !cacheDir.mkdirs())) {
                return null
            }
            val safeName = sanitizeStagingFileName(fileName)
            val destFile = uniqueDestination(cacheDir, safeName)
            partialFile = destFile

            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.takeIf { it.isFile }?.absolutePath
        } catch (e: Exception) {
            partialFile?.delete()
            Log.e("DocumentPathResolver", "Failed to copy URI to cache: $uri", e)
            null
        }
    }

    private fun uniqueDestination(directory: File, fileName: String): File {
        val initial = File(directory, fileName).canonicalFile
        require(initial.parentFile == directory.canonicalFile)
        if (!initial.exists()) return initial

        val extension = fileName.substringAfterLast('.', "").takeIf(String::isNotBlank)
        val stem = if (extension == null) fileName else fileName.removeSuffix(".$extension")
        for (index in 1..10_000) {
            val candidateName = if (extension == null) "$stem-$index" else "$stem-$index.$extension"
            val candidate = File(directory, candidateName).canonicalFile
            if (candidate.parentFile == directory.canonicalFile && !candidate.exists()) return candidate
        }
        error("Unable to allocate a unique staging file")
    }

    private fun resolveExternalStoragePath(uri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: uri.lastPathSegment
            ?: return null

        val cleanDocId = if (documentId.startsWith("tree/")) documentId.removePrefix("tree/") else documentId
        val parts = cleanDocId.split(':', limit = 2)
        if (parts.isEmpty()) return null

        val volume = parts[0]
        val relativePath = parts.getOrNull(1).orEmpty()

        return when {
            volume.equals("primary", ignoreCase = true) -> {
                val base = Environment.getExternalStorageDirectory()
                if (relativePath.isBlank()) base.absolutePath else File(base, relativePath).absolutePath
            }

            volume.equals("home", ignoreCase = true) -> {
                val base = File(Environment.getExternalStorageDirectory(), "Documents")
                if (relativePath.isBlank()) base.absolutePath else File(base, relativePath).absolutePath
            }

            volume.startsWith("/") -> volume
            else -> {
                val storageFile = File("/storage/$volume", relativePath)
                if (storageFile.exists() || File("/storage/$volume").exists()) {
                    storageFile.absolutePath
                } else {
                    File("/mnt/media_rw/$volume", relativePath).takeIf { it.exists() }?.absolutePath
                        ?: storageFile.absolutePath
                }
            }
        }
    }

    private fun findFileInPersistedTree(context: Context, targetUri: Uri, fileName: String): String? {
        val persistedTrees = context.contentResolver.persistedUriPermissions
            .mapNotNull { permission -> DocumentFile.fromTreeUri(context, permission.uri) }

        for (tree in persistedTrees) {
            val resolved = findFileRecursive(tree, targetUri, fileName)
            if (resolved != null) return resolved
        }

        return null
    }

    private fun findFileRecursive(root: DocumentFile, targetUri: Uri, fileName: String): String? {
        for (child in root.listFiles()) {
            if (child.uri == targetUri) {
                return resolveExternalStoragePath(child.uri)
            }

            if (child.isDirectory) {
                val nested = findFileRecursive(child, targetUri, fileName)
                if (nested != null) return nested
            } else if (child.name == fileName) {
                val direct = resolveExternalStoragePath(child.uri)
                if (direct != null) return direct
            }
        }

        return null
    }

    internal fun sanitizeStagingFileName(fileName: String): String {
        val leaf = fileName.replace('\\', '/').substringAfterLast('/')
        val safe = leaf
            .filter { it.code >= 0x20 && it != '\u007f' }
            .replace(Regex("[^\\p{L}\\p{N}._() +\\-]+"), "-")
            .trim(' ', '.')
            .take(180)
        return safe.ifBlank { "install-content.bin" }
    }

    internal fun isPathWithinRoot(root: File, candidate: File): Boolean {
        val canonicalRoot = root.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        return canonicalCandidate == canonicalRoot ||
            canonicalCandidate.path.startsWith(canonicalRoot.path + File.separator)
    }
}
