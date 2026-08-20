package com.sbro.emucorec.data

import com.sbro.emucorec.core.Ps3IsoParser
import java.io.File

/**
 * Collapses all library candidates for a TITLE_ID into one launchable entry.
 *
 * PS3 game updates use category GD and contain their own EBOOT.BIN. They are
 * metadata overlays, not standalone disc sources. Treating them as ordinary
 * bootable folders loses the ISO/disc mount and makes the core fail with
 * "Disc directory not found".
 */
internal object InstalledGameCandidateMerger {
    private const val GAME_DATA_CATEGORY = "GD"

    fun merge(candidates: List<InstalledPs3Game>): List<InstalledPs3Game> {
        return candidates
            .groupBy { it.titleId.trim().uppercase() }
            .values
            .mapNotNull(::mergeTitleCandidates)
    }

    internal fun mergeTitleCandidates(candidates: List<InstalledPs3Game>): InstalledPs3Game? {
        val launchCandidates = candidates.filterNot(::isGameUpdate)
        if (launchCandidates.isEmpty()) return null

        val launchSource = launchCandidates.maxWithOrNull(
            compareBy<InstalledPs3Game>(::launchPriority)
                .thenBy { it.version.orEmpty() }
        ) ?: return null

        val newestUpdate = candidates
            .filter(::isGameUpdate)
            .maxWithOrNull { left, right -> compareVersions(left.version, right.version) }

        if (newestUpdate == null || compareVersions(newestUpdate.version, launchSource.version) < 0) {
            return launchSource
        }

        // Keep every launch-related field from the base game. Only metadata
        // that the update legitimately supersedes is taken from the update.
        return launchSource.copy(
            title = newestUpdate.title.takeIf(String::isNotBlank) ?: launchSource.title,
            contentId = newestUpdate.contentId ?: launchSource.contentId,
            saveDataId = newestUpdate.saveDataId ?: launchSource.saveDataId,
            version = newestUpdate.version ?: launchSource.version,
            iconPath = newestUpdate.iconPath ?: launchSource.iconPath
        )
    }

    private fun isGameUpdate(game: InstalledPs3Game): Boolean =
        game.category.equals(GAME_DATA_CATEGORY, ignoreCase = true)

    private fun launchPriority(game: InstalledPs3Game): Int {
        val source = File(game.installPath)
        return when {
            source.isFile && Ps3IsoParser.isIsoImage(source) -> 400
            game.isCustomFolderGame -> 300
            game.category.equals("DG", ignoreCase = true) -> 200
            else -> 100
        }
    }

    internal fun compareVersions(left: String?, right: String?): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        val count = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until count) {
            val comparison = (leftParts.getOrNull(index) ?: 0)
                .compareTo(rightParts.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun versionParts(version: String?): List<Int> =
        version.orEmpty()
            .split('.')
            .map { component -> component.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
