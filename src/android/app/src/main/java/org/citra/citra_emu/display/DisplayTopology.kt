// Copyright 2025-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.display

import android.hardware.display.DisplayManager
import android.view.Display
import android.view.MotionEvent
import android.view.View
import org.citra.citra_emu.NativeLibrary

/**
 * A snapshot of a single logical display, decoupled from the live [android.view.Display]
 * so the dual-screen topology and the swap decision can be reasoned about — and unit-tested —
 * without a device. The Android side (SecondaryDisplay, ScreenAdjustmentUtil) fills this in
 * from the DisplayManager; everything here is plain data and pure logic.
 */
data class DisplayPanel(
    val displayId: Int,
    val name: String,
    val off: Boolean,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val presentation: Boolean,
    val valid: Boolean,
    // True when the display's mode (its physical panel size) was available. The partitioned
    // split relies on the physical size, so it is only applied when this is set.
    val hasMode: Boolean,
    // True for the system default display (id 0). On an equal-panel clamshell the physical
    // upper/lower position of a panel depends on *which* display it is, not just the device
    // rotation, so the position lookup (equalPanelPosition) needs to know this.
    val isDefault: Boolean
) {
    // Native panel area, for comparing physical panel sizes.
    val area: Int get() = physicalWidth * physicalHeight

    /**
     * Whether [this] is a usable host for the second 3DS screen: a real, on, valid display
     * that is not the primary one and not the app's own hidden virtual display. This is the
     * single source of truth shared by the host side (SecondaryDisplay) and the swap side
     * (ScreenAdjustmentUtil), so the two cannot disagree about which display is secondary.
     */
    fun isUsableSecondary(primaryDisplayId: Int): Boolean =
        displayId != primaryDisplayId && name != "HiddenDisplay" && !off && valid
}

/**
 * How the device's screens map to the two 3DS screens, independent of the host mechanism used
 * (a real second logical display, a partitioned display, or a presentation panel).
 */
sealed class DisplayTopology {
    /** Two distinct logical displays, one per 3DS screen (LG G8X clamshell, an add-on panel). */
    data class RealPair(val primary: DisplayPanel, val secondary: DisplayPanel) : DisplayTopology()

    /**
     * One logical display the app's window is confined to half of (Surface Duo 2); the window
     * bounds are in the display's current capture space.
     */
    data class Partitioned(
        val display: DisplayPanel,
        val windowLeft: Int,
        val windowTop: Int,
        val windowWidth: Int,
        val windowHeight: Int
    ) : DisplayTopology()

    /** No second panel; the game shows a single 3DS screen. */
    data object Single : DisplayTopology()
}

/**
 * Where a panel sits *physically* on the device (as opposed to the 3DS top/bottom it shows).
 * UPPER is the panel at the hinge-end-top of an open clamshell; LOWER the one toward the user.
 */
enum class PanelPosition { UPPER, LOWER }

/**
 * Physical position of [panel] on an equal-panel clamshell (the LG G8X) at a device [rotation].
 *
 * Rotation alone cannot decide this: [rotation] is device-wide, so both panels report the same
 * value, yet which one is upper is set by *which* display the primary window is on. The panels
 * are fixed in the hardware, so the mapping is the (isDefault, rotation) table below. (On API
 * 33+ `Display.getRelativeBounds()` would supply this generically; the G8X is Android 12, so the
 * table stands in for it.)
 *
 * | primary window on | rotation 1 | rotation 3 |
 * |-------------------|------------|------------|
 * | display 0 (default) | UPPER | LOWER |
 * | display 1 (cover)   | LOWER | UPPER |
 *
 * Only landscape rotations (1/3) place the host window full-bleed on one panel; the clamshell
 * correction is only consulted in those, so the non-landscape case is grouped with rotation 1.
 */
fun equalPanelPosition(panel: DisplayPanel, rotation: Int): PanelPosition =
    when {
        panel.isDefault -> if (rotation == 3) PanelPosition.LOWER else PanelPosition.UPPER
        else -> if (rotation == 3) PanelPosition.UPPER else PanelPosition.LOWER
    }

/**
 * Classifies the screen arrangement from the primary panel, the candidate second panels (already
 * filtered by [DisplayPanel.isUsableSecondary]) and the primary window bounds.
 */
fun classifyTopology(
    primary: DisplayPanel,
    candidates: List<DisplayPanel>,
    windowLeft: Int,
    windowTop: Int,
    windowWidth: Int,
    windowHeight: Int,
    rotation: Int
): DisplayTopology {
    val secondary = candidates.firstOrNull()
    if (secondary != null) {
        return DisplayTopology.RealPair(primary, secondary)
    }
    // No second logical display: on a landscape rotation the window may be confined to one
    // half of a wider physical display (the OS partitions it into per-task regions).
    if (rotation != 1 && rotation != 3) {
        return DisplayTopology.Single
    }
    if (!primary.hasMode) {
        return DisplayTopology.Single
    }
    // At rotation 1/3 the display's natural width is the capture-space height.
    val physicalHeight = primary.physicalWidth
    val onHalf = windowHeight < physicalHeight * 3 / 4 && windowTop > 0
    return if (onHalf) {
        DisplayTopology.Partitioned(primary, windowLeft, windowTop, windowWidth, windowHeight)
    } else {
        DisplayTopology.Single
    }
}

/**
 * The effective "Swap Screens" value to hand to the core for the given topology: the user's
 * choice corrected so that, with no user swap, the 3DS *top* screen lands on the physically
 * *upper* panel (and the 3DS bottom on the lower one); the user's swap inverts that. The
 * correction compensates for which physical panel the primary window happens to sit on, so the
 * 3DS top/bottom mapping is stable regardless of the launch display or the 180-degree rotation.
 *
 * Two deliberately-distinct vocabularies run through this file: UPPER/LOWER (PanelPosition) is
 * where a panel sits *physically* on the device, whereas 3DS top/bottom is which emulated
 * screen the user means. The correction exists to keep those aligned (3DS top on the upper
 * panel) even though clamshell hardware can pull them apart.
 */
fun effectiveSwap(userSwap: Boolean, topology: DisplayTopology, rotation: Int): Boolean {
    return when (topology) {
        is DisplayTopology.RealPair -> {
            val p = topology.primary
            val s = topology.secondary
            // An external presentation panel (an add-on display) is mounted above the built-in
            // one, so the 3DS top screen belongs on it. Where both or neither panel is
            // presentation-capable, fall back to the panel size (top screen on the larger), and
            // for equal-size panels (a clamshell such as the LG G8X) to the physical position
            // of the primary's own display: on a clamshell which panel is on top depends on
            // which display the window is on as well as the 180-degree rotation.
            val primaryOnLowerPanel =
                if (p.presentation != s.presentation) {
                    s.presentation
                } else if (p.area != s.area) {
                    p.area < s.area
                } else {
                    equalPanelPosition(p, rotation) == PanelPosition.LOWER
                }
            userSwap xor primaryOnLowerPanel
        }
        // A partitioned display only exists at a landscape rotation (see classifyTopology), so
        // the confinement already selects the half; a bottom-half window needs the correction.
        is DisplayTopology.Partitioned -> {
            val physicalHeight = topology.display.physicalWidth
            val onBottomHalf = topology.windowHeight < physicalHeight * 3 / 4 && topology.windowTop > 0
            userSwap != onBottomHalf
        }
        is DisplayTopology.Single -> userSwap
    }
}

/**
 * Snapshots a live display into the android-free [DisplayPanel] the topology and swap logic
 * operate on. Shared by the host side (SecondaryDisplay) and the swap side (ScreenAdjustmentUtil)
 * so the "which display is usable" and "is it a presentation panel" answers agree everywhere.
 *
 * The physical size comes from the display's mode (the true panel size); getRealSize()/
 * Display.width are hooked on some dual-panel devices to report the per-task partition, so they
 * are only used when the mode is unavailable.
 *
 * Callers that panel several displays at once should compute [presentationIds] once and pass it
 * in, instead of re-querying the presentation category per display.
 */
@Suppress("DEPRECATION")
fun Display.panelOf(displayManager: DisplayManager, presentationIds: Set<Int>): DisplayPanel {
    val mode = mode
    return DisplayPanel(
        displayId = displayId,
        name = name,
        off = state == Display.STATE_OFF,
        physicalWidth = mode?.physicalWidth ?: width,
        physicalHeight = mode?.physicalHeight ?: height,
        presentation = displayId in presentationIds,
        valid = isValid,
        hasMode = mode != null,
        isDefault = displayId == Display.DEFAULT_DISPLAY
    )
}

/** The display ids of the displays in [DisplayManager.DISPLAY_CATEGORY_PRESENTATION]. */
fun presentationIds(displayManager: DisplayManager): Set<Int> =
    displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        .map { it.displayId }.toSet()

/**
 * Forwards single-finger touches to the native secondary window; the emulator ignores them
 * unless the secondary layout shows the bottom (touch) screen. Shared by the Presentation and
 * Activity secondary hosts, which only differ in how they obtain their surface.
 */
fun View.setOnSecondaryTouchForwarder() {
    var pointerId = -1
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                if (pointerId == -1) {
                    pointerId = event.getPointerId(event.actionIndex)
                    NativeLibrary.onSecondaryTouchEvent(
                        event.getX(event.actionIndex), event.getY(event.actionIndex), true)
                }

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index != -1) {
                    NativeLibrary.onSecondaryTouchMoved(event.getX(index), event.getY(index))
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL ->
                if (event.getPointerId(event.actionIndex) == pointerId) {
                    NativeLibrary.onSecondaryTouchEvent(0f, 0f, false)
                    pointerId = -1
                }
        }
        true
    }
}
