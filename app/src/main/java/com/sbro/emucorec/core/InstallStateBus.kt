package com.sbro.emucorec.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object InstallStateBus {
    // replay = 1: an event emitted before any subscriber exists (e.g. the
    // games folder picked during onboarding, before the library screen is
    // composed) is delivered to the subscriber when it starts collecting,
    // instead of being lost and leaving the library empty until a manual
    // refresh.
    private val _events = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun notifyCompleted() {
        _events.tryEmit(System.currentTimeMillis())
    }
}
