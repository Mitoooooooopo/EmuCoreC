package com.sbro.emucorec.data

data class InstalledPs3Game(
    val titleId: String,
    val title: String,
    val contentId: String?,
    val saveDataId: String?,
    val version: String?,
    val category: String?,
    val iconPath: String?,
    val catalogCoverUrl: String?,
    val installPath: String,
    val isCustomFolderGame: Boolean = false
)

enum class Ps3CompatibilityState {
    UNKNOWN,
    NOTHING,
    BOOTABLE,
    INTRO,
    MENU,
    INGAME_LESS,
    INGAME_MORE,
    PLAYABLE
}

data class Ps3CompatibilitySummary(
    val matchedTitleId: String,
    val issueId: Int,
    val state: Ps3CompatibilityState,
    val updatedAtEpochSeconds: Long?,
    val candidateTitleIds: List<String> = listOf(matchedTitleId)
)

data class Ps3CatalogEntry(
    val igdbId: Long,
    val name: String,
    val year: Int?,
    val rating: Float?,
    val summary: String?,
    val coverUrl: String?,
    val heroUrl: String?,
    val genres: List<String> = emptyList(),
    val serials: List<String> = emptyList(),
    val compatibility: Ps3CompatibilitySummary? = null
)

data class Ps3CatalogDetails(
    val igdbId: Long,
    val name: String,
    val year: Int?,
    val rating: Float?,
    val summary: String?,
    val coverUrl: String?,
    val heroUrl: String?,
    val genres: List<String>,
    val serials: List<String>,
    val screenshots: List<String>,
    val videos: List<String>,
    val compatibility: Ps3CompatibilitySummary? = null
)

enum class ProfileGameStatus {
    PLAYING,
    WANT_TO_PLAY,
    COMPLETED,
    DROPPED
}

data class ProfileGameListEntry(
    val igdbId: Long,
    val status: ProfileGameStatus?,
    val isFavorite: Boolean,
    val addedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

data class ProfileCatalogGame(
    val profile: ProfileGameListEntry,
    val catalog: Ps3CatalogEntry
)

data class Ps3PatchInfo(
    val hash: String,
    val name: String,
    val author: String,
    val notes: String,
    val version: String,
    val appVersion: String,
    val game: String,
    val enabled: Boolean
)
