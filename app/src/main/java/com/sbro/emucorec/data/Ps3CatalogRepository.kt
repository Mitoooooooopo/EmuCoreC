package com.sbro.emucorec.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

class Ps3CatalogRepository(private val context: Context) {
    private val assetName = "catalog/games.db"
    private val localDbName = "ps3_games.db"

    fun hasCatalog(): Boolean = getCatalogCount() > 0

    fun getCatalogCount(): Int = querySingleInt("SELECT COUNT(*) FROM games")

    fun getAvailableGenres(): List<String> {
        return openDatabase()?.use { database ->
            database.rawQuery(
                """
                SELECT DISTINCT genre_name
                FROM game_genres
                WHERE genre_name IS NOT NULL AND TRIM(genre_name) <> ''
                ORDER BY genre_name COLLATE NOCASE ASC
                """.trimIndent(),
                emptyArray()
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        cursor.getString(0)?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }
        }.orEmpty()
    }

    fun getAvailableYears(): List<Int> {
        return openDatabase()?.use { database ->
            database.rawQuery(
                """
                SELECT DISTINCT year
                FROM games
                WHERE year IS NOT NULL
                ORDER BY year DESC
                """.trimIndent(),
                emptyArray()
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        if (!cursor.isNull(0)) add(cursor.getInt(0))
                    }
                }
            }
        }.orEmpty()
    }

    fun search(
        query: String,
        genre: String? = null,
        year: Int? = null,
        minRating: Float? = null,
        limit: Int = 80,
        offset: Int = 0
    ): List<Ps3CatalogEntry> {
        val trimmed = query.trim()
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (trimmed.isNotBlank()) {
            conditions += "(g.normalized_name LIKE ? OR g.name LIKE ?)"
            val like = "%${trimmed.lowercase()}%"
            args += like
            args += "%$trimmed%"
        }
        if (!genre.isNullOrBlank()) {
            conditions += "EXISTS (SELECT 1 FROM game_genres gg WHERE gg.igdb_id = g.igdb_id AND gg.genre_name = ?)"
            args += genre
        }
        if (year != null) {
            conditions += "g.year = ?"
            args += year.toString()
        }
        if (minRating != null) {
            conditions += "g.rating >= ?"
            args += minRating.toString()
        }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val sql = """
            SELECT g.igdb_id, g.name, g.year, g.rating, g.summary, g.cover_url, g.hero_url
            FROM games g
            $whereClause
            ORDER BY g.rating DESC, g.name COLLATE NOCASE ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        args += limit.toString()
        args += offset.toString()

        return openDatabase()?.use { database ->
            database.rawQuery(sql, args.toTypedArray()).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val igdbId = cursor.getLong(0)
                        add(
                            Ps3CatalogEntry(
                                igdbId = igdbId,
                                name = cursor.getString(1).orEmpty(),
                                year = cursor.takeIf { !it.isNull(2) }?.getInt(2),
                                rating = cursor.takeIf { !it.isNull(3) }?.getFloat(3),
                                summary = cursor.getString(4),
                                coverUrl = cursor.getString(5),
                                heroUrl = cursor.getString(6),
                                genres = loadGenres(database, igdbId),
                                serials = loadSerials(database, igdbId)
                            )
                        )
                    }
                }
            }
        }.orEmpty()
    }

    fun findBestMatch(gameName: String): Ps3CatalogEntry? {
        val query = gameName.trim()
        if (query.isBlank()) return null
        return search(query = query, limit = 25).firstOrNull {
            it.name.equals(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
        } ?: search(query = query, limit = 1).firstOrNull()
    }

    /** Exact installed-game lookup. PS3 TITLE_ID is read from PARAM.SFO by the core. */
    fun findBySerial(serial: String): Ps3CatalogEntry? {
        val normalized = serial.trim().uppercase().takeIf(String::isNotBlank) ?: return null
        return openDatabase()?.use { database ->
            database.rawQuery(
                """
                SELECT g.igdb_id, g.name, g.year, g.rating, g.summary, g.cover_url, g.hero_url
                FROM games g
                INNER JOIN game_serials s ON s.igdb_id = g.igdb_id
                WHERE UPPER(REPLACE(REPLACE(s.serial, '-', ''), ' ', '')) = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(normalized.replace("-", "").replace(" ", ""))
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val igdbId = cursor.getLong(0)
                Ps3CatalogEntry(
                    igdbId = igdbId,
                    name = cursor.getString(1).orEmpty(),
                    year = cursor.takeIf { !it.isNull(2) }?.getInt(2),
                    rating = cursor.takeIf { !it.isNull(3) }?.getFloat(3),
                    summary = cursor.getString(4),
                    coverUrl = cursor.getString(5),
                    heroUrl = cursor.getString(6),
                    genres = loadGenres(database, igdbId),
                    serials = loadSerials(database, igdbId),
                )
            }
        }
    }

    /** Resolves library box art in one database query instead of opening the catalog per tile. */
    fun findCoverUrls(games: Collection<InstalledPs3Game>): Map<String, String> {
        if (games.isEmpty()) return emptyMap()
        return openDatabase()?.use { database ->
            buildMap {
                for (game in games) {
                    val serialKey = normalizeSerial(game.titleId)
                    var coverUrl: String? = null

                    // 1. Try match by serial if serial table is populated
                    if (serialKey.isNotBlank()) {
                        database.rawQuery(
                            """
                            SELECT g.cover_url
                            FROM game_serials s
                            INNER JOIN games g ON g.igdb_id = s.igdb_id
                            WHERE UPPER(REPLACE(REPLACE(s.serial, '-', ''), ' ', '')) = ?
                              AND g.cover_url IS NOT NULL
                              AND TRIM(g.cover_url) <> ''
                            LIMIT 1
                            """.trimIndent(),
                            arrayOf(serialKey)
                        ).use { cursor ->
                            if (cursor.moveToFirst()) {
                                coverUrl = cursor.getString(0)
                            }
                        }
                    }

                    // 2. Try match by title normalization
                    if (coverUrl.isNullOrBlank()) {
                        val normTitle = normalizeTitle(game.title)
                        if (normTitle.isNotBlank()) {
                            // Exact normalized match
                            database.rawQuery(
                                """
                                SELECT cover_url
                                FROM games
                                WHERE normalized_name = ?
                                  AND cover_url IS NOT NULL
                                  AND TRIM(cover_url) <> ''
                                LIMIT 1
                                """.trimIndent(),
                                arrayOf(normTitle)
                            ).use { cursor ->
                                if (cursor.moveToFirst()) {
                                    coverUrl = cursor.getString(0)
                                }
                            }

                            // Substring match
                            if (coverUrl.isNullOrBlank()) {
                                database.rawQuery(
                                    """
                                    SELECT cover_url
                                    FROM games
                                    WHERE normalized_name LIKE ?
                                      AND cover_url IS NOT NULL
                                      AND TRIM(cover_url) <> ''
                                    LIMIT 1
                                    """.trimIndent(),
                                    arrayOf("%$normTitle%")
                                ).use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        coverUrl = cursor.getString(0)
                                    }
                                }
                            }

                            // Word token match
                            if (coverUrl.isNullOrBlank()) {
                                val tokens = normTitle.split(" ")
                                    .map(String::trim)
                                    .filter { it.length > 2 }
                                if (tokens.isNotEmpty()) {
                                    val where = tokens.joinToString(" AND ") { "normalized_name LIKE ?" }
                                    val args = tokens.map { "%$it%" }.toTypedArray()
                                    database.rawQuery(
                                        """
                                        SELECT cover_url
                                        FROM games
                                        WHERE $where
                                          AND cover_url IS NOT NULL
                                          AND TRIM(cover_url) <> ''
                                        LIMIT 1
                                        """.trimIndent(),
                                        args
                                    ).use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            coverUrl = cursor.getString(0)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!coverUrl.isNullOrBlank()) {
                        put(game.titleId, coverUrl)
                        put(serialKey, coverUrl)
                    }
                }
            }
        }.orEmpty()
    }

    /** Resolves library box art in one database query instead of opening the catalog per tile. */
    fun findCoverUrlsBySerials(serials: Collection<String>): Map<String, String> {
        val normalized = serials
            .map(::normalizeSerial)
            .filter(String::isNotBlank)
            .distinct()
        if (normalized.isEmpty()) return emptyMap()
        val placeholders = normalized.joinToString(",") { "?" }
        return openDatabase()?.use { database ->
            database.rawQuery(
                """
                SELECT UPPER(REPLACE(REPLACE(s.serial, '-', ''), ' ', '')), g.cover_url
                FROM game_serials s
                INNER JOIN games g ON g.igdb_id = s.igdb_id
                WHERE UPPER(REPLACE(REPLACE(s.serial, '-', ''), ' ', '')) IN ($placeholders)
                  AND g.cover_url IS NOT NULL
                  AND TRIM(g.cover_url) <> ''
                """.trimIndent(),
                normalized.toTypedArray()
            ).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(0), cursor.getString(1))
                    }
                }
            }
        }.orEmpty()
    }

    fun getDetails(igdbId: Long): Ps3CatalogDetails? {
        return openDatabase()?.use { database ->
            database.rawQuery(
                """
                SELECT igdb_id, name, year, rating, summary, cover_url, hero_url
                FROM games
                WHERE igdb_id = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(igdbId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                Ps3CatalogDetails(
                    igdbId = cursor.getLong(0),
                    name = cursor.getString(1).orEmpty(),
                    year = cursor.takeIf { !it.isNull(2) }?.getInt(2),
                    rating = cursor.takeIf { !it.isNull(3) }?.getFloat(3),
                    summary = cursor.getString(4),
                    coverUrl = cursor.getString(5),
                    heroUrl = cursor.getString(6),
                    genres = loadGenres(database, igdbId),
                    serials = loadSerials(database, igdbId),
                    screenshots = loadScreenshots(database, igdbId),
                    videos = loadVideos(database, igdbId)
                )
            }
        }
    }

    fun getEntries(igdbIds: Collection<Long>): List<Ps3CatalogEntry> {
        val ids = igdbIds.distinct()
        if (ids.isEmpty()) return emptyList()
        return openDatabase()?.use { database ->
            ids.mapNotNull { igdbId ->
                database.rawQuery(
                    """
                    SELECT igdb_id, name, year, rating, summary, cover_url, hero_url
                    FROM games
                    WHERE igdb_id = ?
                    LIMIT 1
                    """.trimIndent(),
                    arrayOf(igdbId.toString())
                ).use { cursor ->
                    if (!cursor.moveToFirst()) return@mapNotNull null
                    Ps3CatalogEntry(
                        igdbId = cursor.getLong(0),
                        name = cursor.getString(1).orEmpty(),
                        year = cursor.takeIf { !it.isNull(2) }?.getInt(2),
                        rating = cursor.takeIf { !it.isNull(3) }?.getFloat(3),
                        summary = cursor.getString(4),
                        coverUrl = cursor.getString(5),
                        heroUrl = cursor.getString(6),
                        genres = loadGenres(database, igdbId),
                        serials = loadSerials(database, igdbId)
                    )
                }
            }
        }.orEmpty()
    }

    fun findBestMatchDetails(gameName: String): Ps3CatalogDetails? {
        val match = findBestMatch(gameName) ?: return null
        return getDetails(match.igdbId)
    }

    private fun querySingleInt(sql: String): Int {
        return openDatabase()?.use { database ->
            database.rawQuery(sql, emptyArray()).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } ?: 0
    }

    private fun loadGenres(database: SQLiteDatabase, igdbId: Long): List<String> {
        return database.rawQuery(
            """
            SELECT genre_name
            FROM game_genres
            WHERE igdb_id = ?
            ORDER BY genre_name COLLATE NOCASE ASC
            """.trimIndent(),
            arrayOf(igdbId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
    }

    private fun loadScreenshots(database: SQLiteDatabase, igdbId: Long): List<String> {
        return database.rawQuery(
            """
            SELECT image_url
            FROM game_screenshots
            WHERE igdb_id = ?
            ORDER BY position ASC
            LIMIT 10
            """.trimIndent(),
            arrayOf(igdbId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let(::add)
                }
            }
        }
    }

    private fun loadSerials(database: SQLiteDatabase, igdbId: Long): List<String> {
        return database.rawQuery(
            """
            SELECT serial
            FROM game_serials
            WHERE igdb_id = ?
            ORDER BY serial COLLATE NOCASE ASC
            """.trimIndent(),
            arrayOf(igdbId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.trim()?.takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
    }

    private fun loadVideos(database: SQLiteDatabase, igdbId: Long): List<String> {
        return database.rawQuery(
            """
            SELECT youtube_id
            FROM game_videos
            WHERE igdb_id = ?
            ORDER BY position ASC
            LIMIT 10
            """.trimIndent(),
            arrayOf(igdbId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let(::add)
                }
            }
        }
    }

    private fun openDatabase(): SQLiteDatabase? {
        val dbFile = prepareLocalDatabase() ?: return null
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull()
    }

    private fun normalizeTitle(title: String): String {
        return title
            .replace(Regex("[\u2122\u00ae\u00a9\u2117]"), "")
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), "")
            .replace(Regex("(?i)^(EA\\s+SPORTS\\s+|SONY\\s+|DISNEY\\s+|SEGA\\s+|CAPCOM\\s+|KONAMI\\s+)"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    private fun normalizeSerial(serial: String): String =
        serial.trim().uppercase().replace("-", "").replace(" ", "")

    private fun prepareLocalDatabase(): File? {
        val target = File(context.filesDir, localDbName)
        val assetLength = runCatching {
            context.assets.openFd(assetName).use { it.length }
        }.getOrDefault(-1L)

        if (target.exists() && target.length() > 0 && (assetLength <= 0 || target.length() == assetLength)) {
            return target
        }
        return runCatching {
            context.assets.open(assetName).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }.getOrNull()
    }
}
