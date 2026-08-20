package com.sbro.emucorec.ui.emulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchButtonPointerTrackerTest {
    @Test
    fun releasingSecondFingerDoesNotReleaseFirstFingerButton() {
        val tracker = TouchButtonPointerTracker()

        assertEquals(TouchButtonTransition.Pressed, tracker.onPointerDown(10))
        assertNull(tracker.onPointerDown(20))
        assertNull(tracker.onPointerUp(20))
        assertTrue(tracker.isPressed)

        assertEquals(TouchButtonTransition.Released, tracker.onPointerUp(10))
        assertFalse(tracker.isPressed)
    }

    @Test
    fun heldPointerSurvivesMultiPointerMove() {
        val tracker = TouchButtonPointerTracker()

        tracker.onPointerDown(41)

        assertNull(tracker.onPointersChanged(setOf(41, 99)))
        assertTrue(tracker.isPressed)
    }

    @Test
    fun missingPointerIsReleasedDefensively() {
        val tracker = TouchButtonPointerTracker()

        tracker.onPointerDown(7)

        assertEquals(TouchButtonTransition.Released, tracker.onPointersChanged(setOf(8)))
        assertFalse(tracker.isPressed)
        assertNull(tracker.onPointersChanged(emptySet()))
    }

    @Test
    fun cancelReleasesExactlyOnceAndAllowsNextGesture() {
        val tracker = TouchButtonPointerTracker()

        tracker.onPointerDown(1)
        assertEquals(TouchButtonTransition.Released, tracker.cancel())
        assertNull(tracker.cancel())
        assertEquals(TouchButtonTransition.Pressed, tracker.onPointerDown(2))
        assertTrue(tracker.isPressed)
    }
}
