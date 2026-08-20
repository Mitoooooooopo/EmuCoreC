package com.sbro.emucorec.core

import java.io.File

internal data class InstallContentPlan(
    val payloads: List<File>,
    val licences: List<File>,
    val isSplitPackage: Boolean,
) {
    val totalUnits: Int
        get() = (if (isSplitPackage) 1 else payloads.size) + licences.size
}

internal object InstallContentPlanner {
    private val numberedPkgExtension = Regex("\\.pkg\\.\\d+$", RegexOption.IGNORE_CASE)
    private val numberedPartSuffix = Regex("(?:[._-](?:part)?\\d+)$", RegexOption.IGNORE_CASE)

    fun create(paths: List<String>): InstallContentPlan {
        val files = paths.map(::File)
            .filter(File::isFile)
            .distinctBy { it.canonicalPath }
            .sortedWith(ArchiveContentInstaller.naturalFileOrder)
        val licences = files.filter(::isLicence)
        val payloads = files - licences.toSet()
        val splitKeys = payloads.mapNotNull(::splitPackageKey).distinct()
        val isSplit = payloads.size > 1 && splitKeys.size == 1 && payloads.all(::isPackagePart)
        return InstallContentPlan(payloads, licences, isSplit)
    }

    private fun isLicence(file: File): Boolean =
        file.extension.equals("rap", true) || file.extension.equals("edat", true)

    internal fun isPackagePart(file: File): Boolean =
        file.name.contains(".pkg", ignoreCase = true)

    internal fun splitPackageKey(file: File): String? {
        if (!isPackagePart(file)) return null
        val lowerName = file.name.lowercase()
        val withoutNumberedExtension = lowerName.replace(numberedPkgExtension, ".pkg")
        val stem = withoutNumberedExtension.removeSuffix(".pkg")
        val stripped = stem.replace(numberedPartSuffix, "")
        return stripped.takeIf { it != stem || numberedPkgExtension.containsMatchIn(lowerName) }
    }
}
