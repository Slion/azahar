// Copyright 2025-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.display

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.activities.EmulationActivity
import org.citra.citra_emu.features.settings.model.BooleanSetting
import org.citra.citra_emu.features.settings.model.IntSetting
import org.citra.citra_emu.utils.Log

class SecondaryDisplay(val context: Context) : DisplayManager.DisplayListener,
    SecondaryDisplayCallback {
    private var pres: SecondaryDisplayPresentation? = null
    private var usingActivityFallback = false
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val vd: VirtualDisplay
    var preferredDisplayId = -1
    var currentDisplayId = -1

    /**
     * True while the second 3DS screen is hosted on a real second panel: a second logical
     * display (e.g. LG G8X), a partitioned display (e.g. Surface Duo 2), or a presentation
     * display. False when there is no secondary panel (fallback to the hidden virtual
     * display). Callers use this to know the device is in dual-screen mode.
     */
    @Volatile
    var isDualScreenActive: Boolean = false
        private set

    val availableDisplays: List<Display>
        get() = getSecondaryDisplays()

    // True on devices that can put each 3DS screen on its own panel: a real second logical
    // display (LG G8X), a presentation display, or a partitioned display currently split into
    // per-task halves (Surface Duo 2). The in-game menu shows its dual-screen toggle only here.
    fun isDualScreenSupported(): Boolean =
        getSecondaryDisplays().isNotEmpty() || findSplitPanelBounds() != null

    init {
        vd = displayManager.createVirtualDisplay(
            "HiddenDisplay",
            1920,
            1080,
            320,
            null,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        )
        displayManager.registerDisplayListener(this, null)
    }

    // Invoked by the secondary surface host (Presentation or Activity) when it provides its
    // surface, and on surface loss.
    override fun onSurfaceChanged(surface: Surface) {
        if (surface.isValid) {
            NativeLibrary.secondarySurfaceChanged(surface)
        } else {
            Log.warning("SecondaryDisplay Attempted to update null or invalid surface")
        }
    }

    override fun onSurfaceDestroyed() {
        NativeLibrary.secondarySurfaceDestroyed()
    }

    private fun getSecondaryDisplays(): List<Display> {
        val currentDisplayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.displayId
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.displayId
        }
        val presentation = presentationIds(displayManager)
        val result = displayManager.displays.filter {
            // The "usable secondary panel" predicate is shared with the swap side (see
            // DisplayPanel.isUsableSecondary) so the host and the top/bottom correction can
            // never disagree about which display is secondary. Do not require
            // DISPLAY_CATEGORY_PRESENTATION: some panels (e.g. LG G8X) are not
            // presentation-capable. updateDisplay() picks Presentation vs Activity per display.
            val panel = it.panelOf(displayManager, presentation)
            val kept = panel.isUsableSecondary(currentDisplayId)
            if (!kept) {
                // Debug-level: this list can change on every display event, so per-display
                // noise does not belong in the regular log.
                Log.debug(
                    "SecondaryDisplay getSecondaryDisplays: excluded ${it.displayId}:${it.name} " +
                        "current=$currentDisplayId state=${it.state} valid=${it.isValid}"
                )
            }
            kept
        }
        Log.debug("SecondaryDisplay getSecondaryDisplays: available=[${result.joinToString { "${it.displayId}:${it.name}" }}]")
        return result
    }

    /**
     * Detects that this window is confined to one half of a wider physical display (Surface Duo 2
     * dual-screen mode, where the OS partitions the single logical display into per-task regions)
     * and returns the bounds of the opposite half, or null when the window is not so partitioned.
     */
    private fun findSplitPanelBounds(): Rect? {
        if (Build.VERSION.SDK_INT < 30) return null
        val activity = context as? android.app.Activity ?: return null
        val display = activity.display
        // The display *mode* reports the true physical size; getRealSize()/Display.width are
        // hooked on the Surface Duo 2 to reflect the per-task partition (our own half), so they
        // must not be used to detect the split.
        val mode = display.mode ?: return null
        // The bounds our window actually occupies, in the display's CURRENT capture space
        // (0,0-based). This space changes with posture and rotation; it is the coordinate
        // system the secondary launch bounds are interpreted in.
        val our = activity.windowManager.currentWindowMetrics.bounds
        // The split runs along the axis the window is confined on: the confined dimension is
        // one half of the display, the free one is the full span. Decide the axis from the
        // confinement ratio rather than from display.rotation, which is not reliable across a
        // fold (the capture space and the reported rotation can disagree in the stacked
        // posture). When confined on height the panels stack (the secondary is above or below);
        // when confined on width they sit side by side (the secondary is to the left or right).
        val stacked = our.height() < our.width() * 3 / 4
        val (physicalWidth, physicalHeight) = if (stacked)
            mode.physicalHeight to mode.physicalWidth
        else
            mode.physicalWidth to mode.physicalHeight
        // Start offset and size of our window along the confined axis, plus the full span of
        // that axis in the oriented physical size.
        val (ourStart, ourSize, fullSize) = if (stacked)
            Triple(our.top, our.height(), physicalHeight)
        else
            Triple(our.left, our.width(), physicalWidth)
        val gutter = (fullSize - ourSize * 2).coerceAtLeast(0)
        val panel = if (ourStart <= 0) {
            // We are on the first half; the secondary goes on the other side.
            if (stacked)
                Rect(0, ourSize + gutter, physicalWidth, physicalHeight)
            else
                Rect(ourSize + gutter, 0, physicalWidth, physicalHeight)
        } else {
            if (stacked)
                Rect(0, 0, physicalWidth, physicalHeight - ourSize - gutter)
            else
                Rect(0, 0, physicalWidth - ourSize - gutter, physicalHeight)
        }
        Log.info(
            "SecondaryDisplay findSplitPanelBounds: panels=${if (stacked) "stacked" else "side-by-side"} " +
                "display=${physicalWidth}x${physicalHeight} our=$our panel=$panel"
        )
        return panel
    }

    fun updateDisplay() {
        // return early if the parent context is dead or dying
        if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
            return
        }

        // Snapshot once: avoid re-querying DisplayManager multiple times below, which
        // previously allowed a transient empty result (e.g. mid display-state change) to
        // throw an uncaught IndexOutOfBoundsException on availableDisplays[0].
        val displays = availableDisplays
        Log.info(
            "SecondaryDisplay updateDisplay: available=[${displays.joinToString { "${it.displayId}:${it.name}" }}] " +
                "enabled=${BooleanSetting.ENABLE_SECONDARY_DISPLAY.boolean} preferred=$preferredDisplayId"
        )

        // Some dual-panel devices (e.g. the Surface Duo 2 in dual-screen mode) never create a
        // second logical display; the OS partitions the one wide display into per-task regions
        // and confines our window to a single half. Host the secondary screen on the opposite
        // half instead of falling back to the hidden virtual display.
        if (displays.isEmpty() &&
            IntSetting.SECONDARY_DISPLAY_LAYOUT.int != SecondaryDisplayLayout.NONE.int &&
            BooleanSetting.ENABLE_SECONDARY_DISPLAY.boolean
        ) {
            val panel = findSplitPanelBounds()
            if (panel != null) {
                if (SecondaryDisplayActivity.isHosting(Display.DEFAULT_DISPLAY, panel)) {
                    Log.info("SecondaryDisplay updateDisplay: already hosting split panel $panel")
                } else {
                    Log.info("SecondaryDisplay updateDisplay: hosting secondary on split panel $panel")
                    currentDisplayId = Display.DEFAULT_DISPLAY
                    NativeLibrary.setSecondaryHostExpected(true)
                    isDualScreenActive = true
                    usingActivityFallback = true
                    SecondaryDisplayActivity.launch(context, Display.DEFAULT_DISPLAY, this, panel)
                }
                return
            }
        }

        val displayToUse: Display? = if (displays.isEmpty() ||
            // Theoretically, the NONE option is no longer selectable, but
            // I am leaving this in for backwards compatibility
            IntSetting.SECONDARY_DISPLAY_LAYOUT.int == SecondaryDisplayLayout.NONE.int ||
            !BooleanSetting.ENABLE_SECONDARY_DISPLAY.boolean
        ) {
            Log.info("SecondaryDisplay updateDisplay: falling back to HiddenDisplay (vd.display id=${vd.display?.displayId})")
            currentDisplayId = -1
            isDualScreenActive = false
            vd.display
        } else if (preferredDisplayId >= 0 &&
            displays.any { it.displayId == preferredDisplayId }
        ) {
            currentDisplayId = preferredDisplayId
            displays.first { it.displayId == preferredDisplayId }
        } else {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val default = dm.displays.first { it.displayId == Display.DEFAULT_DISPLAY }
            // prioritize displays that have a different name from the default display, as
            // some devices such as the Odin 2 create a permanent virtual display with the same
            // name as the default display that should be skipped in most cases
            currentDisplayId = displays.firstOrNull {
                it.name != default.name && !it.name.contains("Built", true)
            }?.displayId
                ?: displays.firstOrNull()?.displayId
                ?: -1
            if (currentDisplayId == -1) null else displays.first { it.displayId == currentDisplayId }
        }

        if (displayToUse == null) {
            Log.info("SecondaryDisplay updateDisplay: no display selected, releasing secondary output")
            releaseSecondaryOutput()
            return
        }

        val isPresentationCapable = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .any { it.displayId == displayToUse.displayId }

        // if our current output is already on the right display via the right
        // mechanism, ignore
        if (isPresentationCapable && pres?.display == displayToUse) {
            Log.info("SecondaryDisplay updateDisplay: presentation already on display ${displayToUse.displayId}")
            return
        }
        if (!isPresentationCapable && SecondaryDisplayActivity.isHosting(displayToUse.displayId)) {
            Log.info("SecondaryDisplay updateDisplay: activity already hosting display ${displayToUse.displayId}")
            return
        }

        Log.info(
            "SecondaryDisplay updateDisplay: switching secondary output to display " +
                "${displayToUse.displayId} (${displayToUse.name}) " +
                "via ${if (isPresentationCapable) "Presentation" else "SecondaryDisplayActivity (fallback)"}"
        )
        // otherwise, create a new presentation (or activity fallback)
        releaseSecondaryOutput()

        // A real (visible) host will provide a surface, so let the emulator wait for it; the
        // hidden virtual display is not a real host and must not delay game start.
        val hostExpected = displayToUse.displayId != vd.display?.displayId
        isDualScreenActive = hostExpected
        NativeLibrary.setSecondaryHostExpected(hostExpected)
        Log.info("SecondaryDisplay updateDisplay: native host expected=$hostExpected")

        if (isPresentationCapable) {
            try {
                pres = SecondaryDisplayPresentation(context, displayToUse, this)
                pres?.show()
                Log.info("SecondaryDisplay updateDisplay: presentation shown on display ${displayToUse.displayId}")
            }
            // catch BadTokenException and InvalidDisplayException,
            // the display became invalid asynchronously, so we can assign to null
            // until onDisplayAdded/Removed/Changed is called and logic retriggered
            catch (_: WindowManager.BadTokenException) {
                pres = null
            } catch (_: WindowManager.InvalidDisplayException) {
                pres = null
            }
        } else {
            usingActivityFallback = true
            SecondaryDisplayActivity.launch(context, displayToUse.displayId, this)
        }
    }

    fun releaseSecondaryOutput() {
        try {
            pres?.dismiss()
        } catch (_: Exception) { }
        pres = null

        if (usingActivityFallback) {
            usingActivityFallback = false
            SecondaryDisplayActivity.finishActive()
        }

        // No secondary host going forward: never make the emulator wait for a surface.
        isDualScreenActive = false
        NativeLibrary.setSecondaryHostExpected(false)
    }

    fun releaseVD() {
        displayManager.unregisterDisplayListener(this)
        vd.release()
    }

    override fun onDisplayAdded(displayId: Int) {
        onDisplayEvent()
    }

    override fun onDisplayRemoved(displayId: Int) {
        onDisplayEvent()
    }

    override fun onDisplayChanged(displayId: Int) {
        onDisplayEvent()
    }

    // Display events fire while the app is backgrounded too (e.g. the cover screen's own launcher
    // takes over during Recents); reconciling then would relaunch the host onto the cover only for
    // the system to tear it down again, thrashing lifecycle state. Reconcile from display events
    // only while the emulation activity is in the foreground -- its onResume re-hosts on the way
    // back.
    private fun onDisplayEvent() {
        val activity = EmulationActivity.runningInstance() ?: return
        // Skip display events while the activity is mid-configuration (a rotation re-hosts
        // itself from onConfigurationChanged); acting on stale bounds would tear down a
        // host that is already being relaunched.
        if (activity.isChangingConfigurations || !EmulationActivity.isForeground()) return
        updateDisplay()
    }
}
class SecondaryDisplayPresentation(
    context: Context,
    display: Display,
    val parent: SecondaryDisplay
) : Presentation(context, display) {
    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        // Initialize SurfaceView
        surfaceView = SurfaceView(context)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.debug("SecondaryDisplay Surface created on display ${display.displayId}")
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.debug("SecondaryDisplay Surface changed: ${width}x$height on display ${display.displayId}")
                val surface = holder.surface
                if (surface != null && surface.isValid) {
                    parent.onSurfaceChanged(surface)
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.debug("SecondaryDisplay Surface destroyed")
                parent.onSurfaceDestroyed()
            }
        })

        // Forward touches to the native secondary window; the emulator ignores them unless
        // the secondary layout shows the bottom (touch) screen.
        this.surfaceView.setOnSecondaryTouchForwarder()

        setContentView(surfaceView) // Set SurfaceView as content
    }
}
