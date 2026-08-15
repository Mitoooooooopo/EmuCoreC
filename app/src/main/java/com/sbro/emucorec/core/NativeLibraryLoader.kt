package com.sbro.emucorec.core

import android.content.Context

/** Loads the RPCS3 JNI glue and initializes its separately packaged RPCS3 core. */
object NativeLibraryLoader {
    fun isNativeSessionInitialized(): Boolean = net.rpcsx.RPCSX.initialized

    fun ensureLoaded(context: Context) {
        Ps3Runtime.ensureInitialized(context.applicationContext)
    }
}
