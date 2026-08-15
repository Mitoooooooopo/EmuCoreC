package com.sbro.emucorec.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest

@Composable
fun UrlImage(
    imageUrl: String?,
    contentDescription: String,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
    fallbackPath: String? = null,
    pinInMemory: Boolean = false
) {
    val context = LocalContext.current
    val bitmapState = produceState<Bitmap?>(
        initialValue = imageUrl?.let { UrlBitmapMemoryCache.get(it) }
            ?: fallbackPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() },
        imageUrl,
        fallbackPath,
        context
    ) {
        val loadedBitmap = withContext(Dispatchers.IO) {
            if (!imageUrl.isNullOrBlank()) {
                // 1. Check in-memory RAM cache
                UrlBitmapMemoryCache.get(imageUrl)?.let { return@withContext it }

                // 2. Check disk cache
                val cachedFile = UrlImageDiskCache.getCachedFile(context, imageUrl)
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                    val diskBitmap = runCatching { BitmapFactory.decodeFile(cachedFile.absolutePath) }.getOrNull()
                    if (diskBitmap != null) {
                        UrlBitmapMemoryCache.put(imageUrl, diskBitmap, pinned = pinInMemory)
                        return@withContext diskBitmap
                    }
                }

                // 3. Fetch from network or content URI and save to disk cache
                val fetchedBitmap = runCatching {
                    if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                        context.contentResolver.openInputStream(Uri.parse(imageUrl))?.use(BitmapFactory::decodeStream)
                    } else {
                        val downloadedFile = UrlImageDiskCache.downloadToCache(context, imageUrl)
                        if (downloadedFile != null && downloadedFile.exists()) {
                            BitmapFactory.decodeFile(downloadedFile.absolutePath)
                        } else {
                            URL(imageUrl).openStream().use(BitmapFactory::decodeStream)
                        }
                    }
                }.getOrNull()

                if (fetchedBitmap != null) {
                    UrlBitmapMemoryCache.put(imageUrl, fetchedBitmap, pinned = pinInMemory)
                    return@withContext fetchedBitmap
                }
            }

            // 4. Fallback local file if network/disk cover is not available
            if (!fallbackPath.isNullOrBlank()) {
                runCatching { BitmapFactory.decodeFile(fallbackPath) }.getOrNull()
            } else {
                null
            }
        }
        value = loadedBitmap
    }
    val bitmap = bitmapState.value

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackLabel.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

object UrlImageDiskCache {
    private const val CACHE_DIR_NAME = "image_cache"

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCachedFile(context: Context, url: String): File? {
        return runCatching {
            val key = hashKey(url)
            val file = File(getCacheDir(context), key)
            if (file.exists() && file.length() > 0) file else null
        }.getOrNull()
    }

    fun downloadToCache(context: Context, url: String): File? {
        return runCatching {
            val cacheDir = getCacheDir(context)
            val key = hashKey(url)
            val tempFile = File(cacheDir, "$key.tmp")
            val targetFile = File(cacheDir, key)

            val conn = URL(url).openConnection()
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.getInputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                targetFile
            } else {
                tempFile.delete()
                null
            }
        }.getOrNull()
    }

    fun getCacheSizeBytes(context: Context): Long {
        return runCatching {
            val dir = getCacheDir(context)
            if (!dir.exists() || !dir.isDirectory) return@runCatching 0L
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    fun clearCache(context: Context): Long {
        return runCatching {
            val dir = getCacheDir(context)
            if (!dir.exists() || !dir.isDirectory) return@runCatching 0L
            var bytesFreed = 0L
            dir.listFiles()?.forEach { file ->
                val size = if (file.isFile) file.length() else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                if (file.deleteRecursively()) {
                    bytesFreed += size
                }
            }
            UrlBitmapMemoryCache.clear()
            bytesFreed
        }.getOrDefault(0L)
    }

    private fun hashKey(url: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object UrlBitmapMemoryCache {
    private val pinnedCache = mutableMapOf<String, Bitmap>()
    private val cache = object : LruCache<String, Bitmap>(calculateCacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    @Synchronized
    fun get(url: String): Bitmap? = pinnedCache[url] ?: cache.get(url)

    @Synchronized
    fun put(url: String, bitmap: Bitmap, pinned: Boolean = false) {
        if (pinned) {
            pinnedCache[url] = bitmap
            return
        }
        if (pinnedCache[url] == null && cache.get(url) == null) {
            cache.put(url, bitmap)
        }
    }

    @Synchronized
    fun clear() {
        pinnedCache.clear()
        cache.evictAll()
    }

    private fun calculateCacheSizeKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        return (maxMemoryKb / 8).coerceAtLeast(8 * 1024)
    }
}
