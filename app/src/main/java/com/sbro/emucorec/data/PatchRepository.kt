package com.sbro.emucorec.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.rpcsx.RPCSX
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PatchRepository(private val context: Context) {

    sealed class DownloadResult {
        data object UpToDate : DownloadResult()
        data class Success(val patchesImported: Int) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    suspend fun downloadPatches(): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val version = RPCSX.instance.patchEngineVersion()
            val url = "https://rpcs3.net/compatibility?patch&api=v1&v=$version"

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "EmuCoreC/1.0")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    return@withContext DownloadResult.Error("HTTP ${connection.responseCode}")
                }
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val returnCode = root.optInt("return_code", -255)

                when {
                    returnCode == 1 -> DownloadResult.UpToDate
                    returnCode < 0 -> DownloadResult.Error("Server error: $returnCode")
                    else -> {
                        val patchContent = root.optString("patch", "")
                        if (patchContent.isEmpty()) {
                            DownloadResult.Error("Empty patch data")
                        } else {
                            val imported = RPCSX.instance.patchesImport(patchContent)
                            if (imported < 0) {
                                DownloadResult.Error("Failed to import patches")
                            } else {
                                DownloadResult.Success(imported)
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Unknown error")
        }
    }

    fun listPatches(serial: String): List<Ps3PatchInfo> {
        return try {
            val json = RPCSX.instance.patchesList(serial)
            val array = JSONArray(json)
            val result = mutableListOf<Ps3PatchInfo>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    Ps3PatchInfo(
                        hash = obj.getString("hash"),
                        name = obj.getString("name"),
                        author = obj.optString("author", ""),
                        notes = obj.optString("notes", ""),
                        version = obj.optString("version", ""),
                        appVersion = obj.optString("appVersion", "all"),
                        game = obj.optString("game", ""),
                        enabled = obj.optBoolean("enabled", false)
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun togglePatch(
        hash: String,
        name: String,
        serial: String,
        appVersion: String,
        enabled: Boolean
    ): Boolean {
        return try {
            RPCSX.instance.patchSetEnabled(hash, name, serial, appVersion, enabled)
        } catch (e: Exception) {
            false
        }
    }
}
