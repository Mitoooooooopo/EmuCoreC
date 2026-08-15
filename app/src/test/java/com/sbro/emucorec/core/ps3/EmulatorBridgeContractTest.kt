package com.sbro.emucorec.core.ps3

import org.junit.Test

class EmulatorBridgeContractTest {
    @Test
    fun activityExposesCallbacksUsedByTheRpcsxAndroidBridge() {
        val activityClass = Emulator::class.java

        activityClass.getDeclaredMethod("setCurrentGameId", String::class.java)
        activityClass.getDeclaredMethod("getBaseStoragePath")
        activityClass.getDeclaredMethod("openPauseMenuFromController")
        activityClass.getDeclaredMethod(
            "setControllerOverlayState",
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
        activityClass.getDeclaredMethod("setControllerOverlayScale", Float::class.javaPrimitiveType)
        activityClass.getDeclaredMethod("setControllerOverlayOpacity", Int::class.javaPrimitiveType)
    }
}
