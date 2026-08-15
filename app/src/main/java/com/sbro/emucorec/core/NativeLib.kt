package com.sbro.emucorec.core

import net.rpcsx.GameRepository
import net.rpcsx.RPCSX

/** Small compatibility facade around the RPCS3 JNI bridge used by EmuCoreC. */
object NativeLib {
    fun prepareFrontend(): Boolean = true
    fun init(runtimePath: String, ps3Path: String): Boolean = RPCSX.initialized
    fun isInitialized(): Boolean = RPCSX.initialized
    fun refreshAppsList() = GameRepository.queueRefresh()
}
