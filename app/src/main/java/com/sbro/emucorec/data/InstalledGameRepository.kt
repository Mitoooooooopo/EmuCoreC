package com.sbro.emucorec.data

import android.content.Context
import com.sbro.emucorec.core.EmulatorStorage
import com.sbro.emucorec.core.Ps3SfoParser
import java.io.File

class InstalledGameRepository {
    fun loadInstalledGames(context: Context): List<InstalledPs3Game> {
        val customFolders = AppPreferences(context).gameDirectories.mapNotNull { raw ->
            val resolved = com.sbro.emucorec.core.DocumentPathResolver.resolveDirectoryPath(context, raw)
                ?: com.sbro.emucorec.core.DocumentPathResolver.resolveFilePath(context, raw)
                ?: raw
            File(resolved).takeIf { it.exists() }
        }

        val customGameDirs = customFolders.flatMap { folder ->
            if (findParamSfo(folder) != null) {
                listOf(folder)
            } else {
                val directChildren = folder.listFiles().orEmpty().filter { it.isDirectory }.toList()
                val directMatches = directChildren.filter { findParamSfo(it) != null }
                val nestedMatches = directChildren.flatMap { child ->
                    if (findParamSfo(child) != null) emptyList()
                    else child.listFiles().orEmpty().filter { it.isDirectory && findParamSfo(it) != null }
                }
                val allFound = directMatches + nestedMatches
                if (allFound.isNotEmpty()) allFound else if (findParamSfo(folder) != null) listOf(folder) else emptyList()
            }
        }

        val customIsoGames = customFolders.flatMap { folder ->
            val directFiles = folder.listFiles().orEmpty().filter { it.isFile && com.sbro.emucorec.core.Ps3IsoParser.isIsoImage(it) }.toList()
            val nestedFiles = folder.listFiles().orEmpty().filter { it.isDirectory }.flatMap { sub ->
                sub.listFiles().orEmpty().filter { it.isFile && com.sbro.emucorec.core.Ps3IsoParser.isIsoImage(it) }
            }
            (directFiles + nestedFiles).mapNotNull { isoFile ->
                com.sbro.emucorec.core.Ps3IsoParser.parse(context, isoFile)
            }
        }

        customGameDirs.forEach { dir ->
            val sfo = findParamSfo(dir) ?: return@forEach
            val metadata = Ps3SfoParser.parse(sfo)
            val tid = metadata.titleId?.takeIf(String::isNotBlank) ?: return@forEach
            FolderGames.link(context, dir.absolutePath, tid)
        }

        customIsoGames.forEach { game ->
            FolderGames.link(context, game.installPath, game.titleId)
        }

        val searchRoots = EmulatorStorage.knownStorageRoots(context).flatMap { storageRoot ->
            listOf(
                File(storageRoot, "ps3/config/dev_hdd0/game"),
                File(storageRoot, "ps3/config/games"),
                File(storageRoot, "ps3/config/dev_hdd0/disc"),
                File(storageRoot, "ps3/directboot"),
                File(storageRoot, "config/dev_hdd0/game"),
                File(storageRoot, "config/games"),
                File(storageRoot, "config/dev_hdd0/disc"),
                File(storageRoot, "directboot")
            )
        }.plus(
            listOf(
                EmulatorStorage.hdd0GameRoot(context),
                EmulatorStorage.discGamesRoot(context),
                EmulatorStorage.hdd0DiscRoot(context),
                FolderGames.linkRoot(context)
            )
        ).distinctBy { it.absolutePath }

        val candidateGameDirs = searchRoots
            .flatMap { root -> root.listFiles().orEmpty().filter { it.isDirectory || FolderGames.isLink(it) }.toList() }
            .plus(customGameDirs)
            .distinctBy { it.absolutePath }

        val installedFromDirs = candidateGameDirs
            .mapNotNull { directory ->
                if (directory.isFile && com.sbro.emucorec.core.Ps3IsoParser.isIsoImage(directory)) {
                    return@mapNotNull com.sbro.emucorec.core.Ps3IsoParser.parse(context, directory)
                }
                val sfo = findParamSfo(directory) ?: EmulatorStorage.paramSfoPath(context, directory.name)
                if (!sfo.isFile) return@mapNotNull null
                val metadata = Ps3SfoParser.parse(sfo)
                val parsedTitleId = metadata.titleId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val parsedTitle = metadata.title?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val iconFile = findIcon(directory) ?: EmulatorStorage.iconPath(context, parsedTitleId)
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

        val directBootGames = FolderGames.entries(context).mapNotNull { (_, targetPath) ->
            val target = File(targetPath)
            if (target.isFile && com.sbro.emucorec.core.Ps3IsoParser.isIsoImage(target)) {
                com.sbro.emucorec.core.Ps3IsoParser.parse(context, target)
            } else null
        }

        val installed = (installedFromDirs + customIsoGames + directBootGames)
            // Prioritise bootable game directories over DLC/patch folders that share the
            // same TITLE_ID but contain no EBOOT.BIN. distinctBy keeps the first entry,
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
        val matchingFolders = findInstalledGameFolders(context, safeTitleId)
        if (matchingFolders.isEmpty()) return false

        val deleted = mutableListOf<File>()
        val failed = mutableListOf<File>()

        FolderGames.removeDirectBootByTitleId(context, safeTitleId)

        EmulatorStorage.knownStorageRoots(context).forEach { storageRoot ->
            val ps3Root = File(storageRoot, "ps3")
            deleteInstalledGameFiles(
                context = context,
                ps3Root = ps3Root,
                titleSegment = safeTitleId,
                deleted = deleted,
                failed = failed
            )
        }

        return findInstalledGameFolders(context, safeTitleId).isEmpty()
    }

    private fun hasBootable(fileOrDir: File): Boolean {
        if (fileOrDir.isFile && com.sbro.emucorec.core.Ps3IsoParser.isIsoImage(fileOrDir)) return true
        return File(fileOrDir, "EBOOT.BIN").isFile ||
               File(fileOrDir, "PS3_GAME/EBOOT.BIN").isFile ||
               File(fileOrDir, "USRDIR/EBOOT.BIN").isFile ||
               File(fileOrDir, "PS3_GAME/USRDIR/EBOOT.BIN").isFile
    }

    private fun findParamSfo(directory: File): File? {
        val direct = File(directory, "PARAM.SFO")
        if (direct.isFile) return direct
        val disc = File(directory, "PS3_GAME/PARAM.SFO")
        if (disc.isFile) return disc
        val usrdir = File(directory, "USRDIR/PARAM.SFO")
        if (usrdir.isFile) return usrdir
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
        val searchSubdirs = listOf(
            "ps3/config/dev_hdd0/game",
            "ps3/config/games",
            "ps3/config/dev_hdd0/disc",
            "ps3/directboot"
        )
        return EmulatorStorage.knownStorageRoots(context)
            .flatMap { storageRoot -> searchSubdirs.map { File(storageRoot, it) } }
            .plus(listOf(FolderGames.linkRoot(context)))
            .flatMap { appRoot -> appRoot.listFiles().orEmpty().filter { it.isDirectory || FolderGames.isLink(it) } }
            .filter { directory ->
                directory.name.equals(titleId, ignoreCase = true) ||
                    findParamSfo(directory)?.let(Ps3SfoParser::parse)?.titleId
                        ?.equals(titleId, ignoreCase = true) == true
            }
            .toSet()
    }

    private fun deleteInstalledGameFiles(
        context: Context,
        ps3Root: File,
        titleSegment: String,
        deleted: MutableList<File>,
        failed: MutableList<File>
    ) {
        if (!isSafePathSegment(titleSegment)) return

        val directBootLink = File(FolderGames.linkRoot(context), titleSegment)
        if (FolderGames.isLink(directBootLink) || directBootLink.exists()) {
            FolderGames.removeDirectBootByTitleId(context, titleSegment)
            deleted += directBootLink
        }

        listOf(
            "config/dev_hdd0/game/$titleSegment",
            "config/games/$titleSegment",
            "config/dev_hdd0/disc/$titleSegment",
            "directboot/$titleSegment"
        ).forEach { relativePath ->
            val target = File(ps3Root, relativePath)
            if (FolderGames.isLink(target)) {
                target.delete()
                deleted += target
            } else {
                deleteRecursively(target, deleted, failed)
            }
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
