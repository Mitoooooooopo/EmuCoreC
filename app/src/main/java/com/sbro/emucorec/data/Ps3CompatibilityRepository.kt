package com.sbro.emucorec.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/** Local cache of the official RPCS3 compatibility export. */
class Ps3CompatibilityRepository(context: Context) {
    private val cacheFile = File(context.applicationContext.filesDir, "compatibility/rpcs3_compatibility.json")

    suspend fun getSnapshot(): Ps3CompatibilitySnapshot = withContext(Dispatchers.IO) {
        synchronized(lock) {
            cachedSnapshot?.takeIf { !it.shouldRefresh() }?.let { return@withContext it }
        }

        val now = System.currentTimeMillis()
        val payload = if (!cacheFile.isFile || now - cacheFile.lastModified() >= REFRESH_INTERVAL_MS) {
            download()?.also { content ->
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(content)
            } ?: cacheFile.takeIf(File::isFile)?.readText()
        } else {
            cacheFile.readText()
        }

        val snapshot = payload?.let { parse(it, now) } ?: Ps3CompatibilitySnapshot.EMPTY.copy(checkedAtMs = now)
        synchronized(lock) { cachedSnapshot = snapshot }
        snapshot
    }

    private fun download(): String? = runCatching {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "EmuCoreC/${BuildConfigVersion.name}")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun parse(payload: String, checkedAtMs: Long): Ps3CompatibilitySnapshot {
        val root = JSONObject(payload)
        if (root.optInt("return_code", -1) < 0) return Ps3CompatibilitySnapshot.EMPTY.copy(checkedAtMs = checkedAtMs)
        val results = root.optJSONObject("results") ?: return Ps3CompatibilitySnapshot.EMPTY.copy(checkedAtMs = checkedAtMs)
        val records = linkedMapOf<String, Ps3CompatibilityRecord>()
        val keys = results.keys()
        while (keys.hasNext()) {
            val titleId = keys.next().uppercase()
            val item = results.optJSONObject(titleId) ?: continue
            records[titleId] = Ps3CompatibilityRecord(
                titleId = titleId,
                state = item.optString("status").toCompatibilityState(),
                updatedAt = item.optString("date").takeIf(String::isNotBlank),
            )
        }
        return Ps3CompatibilitySnapshot(records = records, checkedAtMs = checkedAtMs)
    }

    private fun String.toCompatibilityState(): Ps3CompatibilityState = when (lowercase()) {
        "playable" -> Ps3CompatibilityState.PLAYABLE
        "ingame" -> Ps3CompatibilityState.INGAME_MORE
        "intro" -> Ps3CompatibilityState.INTRO
        "loadable" -> Ps3CompatibilityState.BOOTABLE
        "nothing" -> Ps3CompatibilityState.NOTHING
        else -> Ps3CompatibilityState.UNKNOWN
    }

    private object BuildConfigVersion { const val name = "0.1.0" }

    private companion object {
        const val API_URL = "https://rpcs3.net/compatibility?api=v1&export"
        const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L
        val lock = Any()
        var cachedSnapshot: Ps3CompatibilitySnapshot? = null
    }
}

data class Ps3CompatibilitySnapshot(
    val records: Map<String, Ps3CompatibilityRecord>,
    val checkedAtMs: Long,
) {
    fun shouldRefresh(): Boolean = System.currentTimeMillis() - checkedAtMs >= 24L * 60L * 60L * 1000L

    fun resolve(titleId: String?): Ps3CompatibilitySummary? {
        val key = titleId?.trim()?.uppercase()?.takeIf(String::isNotBlank) ?: return null
        val record = records[key] ?: return null
        return record.toSummary(listOf(key))
    }

    fun resolve(titleIds: List<String>, gameName: String? = null): Ps3CompatibilitySummary? {
        val keys = titleIds.map { it.trim().uppercase() }.filter(String::isNotBlank).distinct()
        val match = keys.firstNotNullOfOrNull { records[it] } ?: return null
        return match.toSummary(keys.ifEmpty { listOf(match.titleId) })
    }

    private fun Ps3CompatibilityRecord.toSummary(candidates: List<String>) = Ps3CompatibilitySummary(
        matchedTitleId = titleId,
        issueId = 0,
        state = state,
        updatedAtEpochSeconds = null,
        candidateTitleIds = candidates,
    )

    companion object {
        val EMPTY = Ps3CompatibilitySnapshot(emptyMap(), 0L)
    }
}

data class Ps3CompatibilityRecord(
    val titleId: String,
    val state: Ps3CompatibilityState,
    val updatedAt: String? = null,
)
