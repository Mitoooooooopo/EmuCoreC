package com.sbro.emucorec.core.ps3

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.sbro.emucorec.core.Ps3Runtime

/** Native rendering target used by the RPCS3 core. */
class EmuSurface(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val emulator = context as? Emulator
    private val doubleTapDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(event: MotionEvent): Boolean {
                return performClick()
            }
        },
    ).apply { setIsLongpressEnabled(false) }

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        doubleTapDetector.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        emulator?.requestOverlayMenuButtonReveal()
        return true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (width > 1 && height > 1) {
            Ps3Runtime.attachSurface(holder.surface, width, height, currentRefreshRate())
            emulator?.onEmulationSurfaceReady()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width > 1 && height > 1) {
            Ps3Runtime.attachSurface(holder.surface, width, height, currentRefreshRate())
            emulator?.onEmulationSurfaceReady()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Ps3Runtime.detachSurface()
    }

    private fun currentRefreshRate(): Double =
        display?.refreshRate?.toDouble()?.takeIf { it.isFinite() && it >= 20.0 } ?: 60.0
}
