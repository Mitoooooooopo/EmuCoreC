package com.sbro.emucorec.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.sbro.emucorec.core.EmulatorStorage
import java.io.File

object FolderGames {
    const val LINK_DIR = "directboot"
    private val linkName = Regex("^[A-Za-z0-9_-]{4,32}$")

    fun linkRoot(context: Context): File {
        return File(EmulatorStorage.ps3Root(context), LINK_DIR)
    }

    fun isLink(file: File): Boolean = runCatching {
        OsConstants.S_ISLNK(Os.lstat(file.absolutePath).st_mode)
    }.getOrDefault(false)

    fun normalize(path: String): String = path.trimEnd('/')

    fun entries(context: Context): List<Pair<File, String>> {
        val root = linkRoot(context)
        val children = root.listFiles() ?: return emptyList()

        return children.filter { isLink(it) }.mapNotNull { link ->
            val target = runCatching { Os.readlink(link.absolutePath) }.getOrNull()
            if (target == null) null else link to normalize(target)
        }
    }

    fun candidatePaths(link: String): List<String> {
        val candidates = mutableListOf(link)

        fun reroot(prefix: String, dropFirstSegment: Boolean) {
            if (!link.startsWith(prefix)) {
                return
            }

            var rest = link.substring(prefix.length)

            if (dropFirstSegment) {
                val slash = rest.indexOf('/')
                if (slash < 0) {
                    return
                }
                rest = rest.substring(slash + 1)
            }

            candidates += "/storage/$rest"
        }

        reroot("/mnt/user/", true)
        reroot("/mnt/runtime/", true)
        reroot("/mnt/androidwritable/", true)
        reroot("/mnt/media_rw/", false)

        return candidates
    }

    fun resolveDescriptorPath(fd: Int): String? {
        val link = runCatching { Os.readlink("/proc/self/fd/$fd") }.getOrNull() ?: return null
        return candidatePaths(link).firstOrNull { File(it).exists() }
    }

    fun gameRootOf(paramSfoPath: String): String {
        val parent = File(paramSfoPath).parentFile ?: return normalize(paramSfoPath)

        if (parent.name == "PS3_GAME") {
            return normalize(parent.parent ?: parent.absolutePath)
        }

        return normalize(parent.absolutePath)
    }

    fun iconPathOf(gameRoot: String): String? {
        listOf("PS3_GAME/ICON0.PNG", "ICON0.PNG").forEach { relative ->
            val candidate = File(gameRoot, relative)
            if (candidate.isFile) {
                return candidate.absolutePath
            }
        }

        return null
    }

    fun linksTo(context: Context, gamePath: String): List<File> {
        val wanted = normalize(gamePath)
        return entries(context).filter { it.second == wanted }.map { it.first }
    }

    fun isDirectBoot(context: Context, gamePath: String): Boolean {
        val root = linkRoot(context).absolutePath
        val normalized = normalize(gamePath)
        return normalized.startsWith(root) || linksTo(context, gamePath).isNotEmpty()
    }

    fun bootPath(context: Context, gamePath: String): String {
        return linksTo(context, gamePath).firstOrNull()?.absolutePath ?: gamePath
    }

    fun link(context: Context, gameRoot: String, titleId: String): File? {
        val safeTitle = titleId.trim().uppercase()
        if (!linkName.matches(safeTitle)) {
            return null
        }

        val root = linkRoot(context)

        if (!root.isDirectory && !root.mkdirs()) {
            return null
        }

        val link = File(root, safeTitle)

        if (isLink(link)) {
            if (runCatching { Os.remove(link.absolutePath) }.isFailure) {
                return null
            }
        } else if (link.exists() && !link.delete()) {
            return null
        }

        return runCatching {
            Os.symlink(normalize(gameRoot), link.absolutePath)
            link
        }.getOrNull()
    }

    fun removeLinks(context: Context, gamePath: String): Int {
        var removed = 0

        linksTo(context, gamePath).forEach { link ->
            if (runCatching { Os.remove(link.absolutePath) }.isSuccess) {
                removed++
            }
        }

        return removed
    }

    fun removeDirectBootByTitleId(context: Context, titleId: String): Boolean {
        val safeTitle = titleId.trim().uppercase()
        val link = File(linkRoot(context), safeTitle)
        if (isLink(link) || link.exists()) {
            return runCatching {
                Os.remove(link.absolutePath)
                true
            }.getOrElse { link.delete() }
        }
        return false
    }
}
