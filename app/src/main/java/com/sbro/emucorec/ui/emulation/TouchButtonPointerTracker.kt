package com.sbro.emucorec.ui.emulation

/**
 * Owns exactly one pointer for an on-screen button.
 *
 * Android sends multi-pointer events containing pointers that belong to other
 * controls. Treating every ACTION_POINTER_UP as this button's release makes a
 * held button pop up as soon as a second finger leaves the screen. Keeping the
 * owner id here makes that behavior explicit and independently testable.
 */
internal class TouchButtonPointerTracker {
    private var activePointerId: Int? = null

    val isPressed: Boolean
        get() = activePointerId != null

    fun onPointerDown(pointerId: Int): TouchButtonTransition? {
        if (activePointerId != null) return null
        activePointerId = pointerId
        return TouchButtonTransition.Pressed
    }

    fun onPointerUp(pointerId: Int): TouchButtonTransition? {
        if (activePointerId != pointerId) return null
        activePointerId = null
        return TouchButtonTransition.Released
    }

    fun onPointersChanged(pointerIds: Set<Int>): TouchButtonTransition? {
        val owner = activePointerId ?: return null
        return if (owner in pointerIds) null else release()
    }

    fun cancel(): TouchButtonTransition? = release()

    private fun release(): TouchButtonTransition? {
        if (activePointerId == null) return null
        activePointerId = null
        return TouchButtonTransition.Released
    }
}

internal enum class TouchButtonTransition {
    Pressed,
    Released,
}
