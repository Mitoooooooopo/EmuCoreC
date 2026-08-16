package com.sbro.emucorec.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide caches for the live RPCS3 settings tree.
 *
 * The core serialises the whole config tree on every settingsGet(""), and the
 * settings UI re-reads it on each tab switch. Caching the raw JSON keeps tab
 * switching instant; it is invalidated after every successful write so stale
 * values are never shown. Resource id lookups are cached the same way: they
 * are constant per APK, and getIdentifier() is reflection-backed and slow.
 */
object CoreSettingsTreeCache {
    @Volatile
    private var treeJson: String? = null

    private val resourceIds = ConcurrentHashMap<String, Int>()

    fun tree(fetch: () -> String): String =
        treeJson ?: fetch().also { treeJson = it }

    fun invalidateTree() {
        treeJson = null
    }

    fun resourceId(name: String, resolve: () -> Int): Int =
        resourceIds.getOrPut(name, resolve)
}
