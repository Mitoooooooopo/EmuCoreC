package com.sbro.emucorec

import android.app.Application
import com.sbro.emucorec.core.EmulatorStorage
import com.sbro.emucorec.core.NativeLibraryLoader
import com.sbro.emucorec.core.PlayTimeRepository
import com.sbro.emucorec.data.AppPreferences

class EmuCoreCApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { EmulatorStorage.prepareRuntime(this) }
        runCatching { PlayTimeRepository(this).finishOpenSessions() }
        if (AppPreferences(this).onboardingCompleted) {
            NativeLibraryLoader.ensureLoaded(this)
        }
    }
}
