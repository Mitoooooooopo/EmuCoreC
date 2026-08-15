package com.sbro.emucorec.data

import android.content.Context
import com.sbro.emucorec.core.EmulatorStorage
import com.sbro.emucorec.core.Ps3SfoParser
import java.io.File

class InstalledGameRepository {
    fun loadInstalledGames(context: Context): List<InstalledPs3Game> {
        val searchRoots = EmulatorStorage.knownStorageRoots(context).flatMap { storageRoot ->
            listOf(
                File(storageRoot, "ps3/config/dev_hdd0/game"),
                File(storageRoot, "ps3/config/games"),
                File(storageRoot, "ps3/config/dev_hdd0/disc"),
                File(storageRoot, "config/dev_hdd0/game"),
                File(storageRoot, "config/games"),
                File(storageRoot, "config/dev_hdd0/disc")
            )
        }.plus(
            listOf(
                EmulatorStorage.hdd0GameRoot(context),
                EmulatorStorage.discGamesRoot(context),
                EmulatorStorage.hdd0DiscRoot(context)
            )
        ).distinctBy { it.absolutePath }
        val installed = searchRoots
            .flatMap { root -> root.listFiles().orEmpty().filter(File::isDirectory).toList() }
            .mapNotNull { directory ->
                val titleId = directory.name
                val sfo = findParamSfo(directory) ?: EmulatorStorage.paramSfoPath(context, titleId)
                if (!sfo.isFile) return@mapNotNull null
                val metadata = Ps3SfoParser.parse(sfo)
                val parsedTitleId = metadata.titleId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val parsedTitle = metadata.title?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val iconFile = findIcon(directory) ?: EmulatorStorage.iconPath(context, titleId)
                InstalledPs3Game(
                    titleId = parsedTitleId,
                    title = parsedTitle,
                    contentId = metadata.contentId,
                    saveDataId = metadata.saveDataId ?: parsedTitleId,
                    version = metadata.version,
                    category = metadata.category,
                    iconPath = iconFile.takeIf { it.exists() }?.absolutePath,
                    catalogCoverUrl = null,
                    installPath = directory.absolutePath
                )
            }
            // Prioritise bootable game directories over DLC/patch folders that share the
            // same TITLE_ID but contain no EBOOT.BIN.  distinctBy keeps the first entry,
            // so we sort bootable entries to the front before deduplication.
            .sortedByDescending { hasBootable(File(it.installPath)) }
            .distinctBy { it.titleId.uppercase() }

        val covers = Ps3CatalogRepository(context.applicationContext)
            .findCoverUrls(installed)
        return installed
            .map { game ->
                game.copy(catalogCoverUrl = covers[game.titleId] ?: covers[normalizeSerial(game.titleId)])
            }
            .sortedBy { it.title.lowercase() }
    }

    fun findByTitleId(context: Context, titleId: String): InstalledPs3Game? {
        return loadInstalledGames(context).firstOrNull { it.titleId.equals(titleId, ignoreCase = true) }
    }

    fun deleteByTitleId(context: Context, titleId: String): Boolean {
        val safeTitleId = titleId.trim().takeIf(::isSafePathSegment) ?: return false
        val deleted = mutableListOf<File>()
        val failed = mutableListOf<File>()

        EmulatorStorage.knownStorageRoots(context).forEach { storageRoot ->
            val ps3Root = File(storageRoot, "ps3")
            deleteInstalledGameFiles(
                ps3Root = ps3Root,
                titleSegment = safeTitleId,
                deleted = deleted,
                failed = failed
            )
        }

        return deleted.isNotEmpty() && failed.isEmpty() && findInstalledGameFolders(context, safeTitleId).isEmpty()
    }

    /**
     * Returns true when [directory] contains a bootable EBOOT.BIN in any of the
     * standard PS3 layout locations.  DLC and patch packages share the base
     * game's TITLE_ID but never ship an EBOOT.BIN, so this is a reliable way to
     * distinguish a launchable game from supplemental content.
     */
    private fun hasBootable(directory: File): Boolean {
        return File(directory, "EBOOT.BIN").isFile ||
               File(directory, "PS3_GAME/EBOOT.BIN").isFile ||
               File(directory, "USRDIR/EBOOT.BIN").isFile ||
               File(directory, "PS3_GAME/USRDIR/EBOOT.BIN").isFile
    }

    private fun findParamSfo(directory: File): File? {
        val direct = File(directory, "PARAM.SFO")
        if (direct.isFile) return direct
        val disc = File(directory, "PS3_GAME/PARAM.SFO")
        if (disc.isFile) return disc
        return null
    }

    private fun findIcon(directory: File): File? {
        val direct = File(directory, "ICON0.PNG")
        if (direct.isFile) return direct
        val disc = File(directory, "PS3_GAME/ICON0.PNG")
        if (disc.isFile) return disc
        return null
    }

    private fun findInstalledGameFolders(context: Context, titleId: String): Set<File> {
        val searchSubdirs = listOf("ps3/config/dev_hdd0/game", "ps3/config/games", "ps3/config/dev_hdd0/disc")
        return EmulatorStorage.knownStorageRoots(context)
            .flatMap { storageRoot -> searchSubdirs.map { File(storageRoot, it) } }
            .flatMap { appRoot -> appRoot.listFiles().orEmpty().filter(File::isDirectory) }
            .filter { directory ->
                directory.name.equals(titleId, ignoreCase = true) ||
                    findParamSfo(directory)?.let(Ps3SfoParser::parse)?.titleId
                        ?.equals(titleId, ignoreCase = true) == true
            }
            .toSet()
    }

    private fun deleteInstalledGameFiles(
        ps3Root: File,
        titleSegment: String,
        deleted: MutableList<File>,
        failed: MutableList<File>
    ) {
        if (!isSafePathSegment(titleSegment)) return
        listOf(
            "config/dev_hdd0/game/$titleSegment",
            "config/games/$titleSegment",
            "config/dev_hdd0/disc/$titleSegment"
        ).forEach { relativePath ->
            deleteRecursively(File(ps3Root, relativePath), deleted, failed)
        }
    }

    private fun deleteRecursively(
        target: File,
        deleted: MutableList<File>,
        failed: MutableList<File>
    ) {
        if (!target.exists()) return
        val removed = runCatching { target.deleteRecursively() }.getOrDefault(false)
        if (removed && !target.exists()) {
            deleted += target
        } else {
            failed += target
        }
    }

    private fun isSafePathSegment(value: String): Boolean {
        return value.isNotBlank() &&
            value != "." &&
            value != ".." &&
            value.none { it == '/' || it == '\\' || it == File.separatorChar }
    }

    private fun normalizeSerial(value: String): String =
        value.trim().uppercase().replace("-", "").replace(" ", "")
}
