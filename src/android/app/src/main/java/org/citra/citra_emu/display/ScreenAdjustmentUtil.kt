// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.display

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import org.citra.citra_emu.features.settings.model.BooleanSetting
import org.citra.citra_emu.features.settings.model.IntListSetting
import org.citra.citra_emu.features.settings.model.IntSetting
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.features.settings.utils.SettingsFile
import org.citra.citra_emu.utils.EmulationMenuSettings

class ScreenAdjustmentUtil(
    private val context: Context,
    private val windowManager: WindowManager,
    private val settings: Settings
) {
    companion object {
        /**
         * On devices where the OS confines the app's primary window to one half of a wider
         * display (e.g. Surface Duo 2 in landscape), which half it lands on depends on the
         * device's orientation. Correct for that so the top 3DS screen always shows on the
         * upper panel with "Swap Screens" off, and on the lower one when on.
         */
        fun effectiveSwapScreens(userSwap: Boolean, activity: Activity): Boolean {
            if (Build.VERSION.SDK_INT < 30) return userSwap
            val display = activity.windowManager.defaultDisplay
            val rotation = @Suppress("DEPRECATION") display.rotation
            if (rotation != 1 && rotation != 3) return userSwap
            // Only partitioned-display devices (one wide display, no real second logical
            // display, e.g. Surface Duo 2) re-home the task between halves on a flip.
            val displays = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            if (displays.displays.any {
                    it.displayId != Display.DEFAULT_DISPLAY &&
                    it.name != "HiddenDisplay" &&
                    it.state != Display.STATE_OFF
                }
            ) return userSwap
            val mode = display.mode ?: return userSwap
            // Rotation 1/3: the display's natural width is the capture-space height.
            val physicalHeight = mode.physicalWidth
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            val onBottomHalf = bounds.height() < physicalHeight * 3 / 4 && bounds.top > 0
            return userSwap != onBottomHalf
        }
    }

    fun swapScreen() {
        val isEnabled = !EmulationMenuSettings.swapScreens
        EmulationMenuSettings.swapScreens = isEnabled
        val activity = context as? Activity
        NativeLibrary.swapScreens(
            if (activity != null) effectiveSwapScreens(isEnabled, activity) else isEnabled,
            windowManager.defaultDisplay.rotation
        )
        BooleanSetting.SWAP_SCREEN.boolean = isEnabled
        settings.saveSetting(BooleanSetting.SWAP_SCREEN, SettingsFile.FILE_NAME_CONFIG)
    }

    fun cycleLayouts() {
        val landscapeLayoutsToCycle = IntListSetting.LAYOUTS_TO_CYCLE.list
        val landscapeValues =
            if (landscapeLayoutsToCycle.isNotEmpty()) {
                landscapeLayoutsToCycle.toIntArray()
            } else {
                context.resources.getIntArray(
                    R.array.landscapeValues
                )
            }
        val portraitValues = context.resources.getIntArray(R.array.portraitValues)

        if (NativeLibrary.isPortraitMode()) {
            val currentLayout = IntSetting.PORTRAIT_SCREEN_LAYOUT.int
            val pos = portraitValues.indexOf(currentLayout)
            val layoutOption = portraitValues[(pos + 1) % portraitValues.size]
            changePortraitOrientation(layoutOption)
        } else {
            val currentLayout = IntSetting.SCREEN_LAYOUT.int
            val pos = landscapeValues.indexOf(currentLayout)
            val layoutOption = landscapeValues[(pos + 1) % landscapeValues.size]
            changeScreenOrientation(layoutOption)
        }
    }

    fun changePortraitOrientation(layoutOption: Int) {
        IntSetting.PORTRAIT_SCREEN_LAYOUT.int = layoutOption
        settings.saveSetting(IntSetting.PORTRAIT_SCREEN_LAYOUT, SettingsFile.FILE_NAME_CONFIG)
        NativeLibrary.reloadSettings()
        NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode())
    }

    fun changeScreenOrientation(layoutOption: Int) {
        IntSetting.SCREEN_LAYOUT.int = layoutOption
        settings.saveSetting(IntSetting.SCREEN_LAYOUT, SettingsFile.FILE_NAME_CONFIG)
        NativeLibrary.reloadSettings()
        NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode())
    }

    fun changeSecondaryOrientation(layoutOption: Int) {
        IntSetting.SECONDARY_DISPLAY_LAYOUT.int = layoutOption
        settings.saveSetting(IntSetting.SECONDARY_DISPLAY_LAYOUT, SettingsFile.FILE_NAME_CONFIG)
        NativeLibrary.reloadSettings()
        NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode())
    }

    fun enableSecondaryDisplay(layoutOption: Int) {
        BooleanSetting.ENABLE_SECONDARY_DISPLAY.boolean = true
        settings.saveSetting(BooleanSetting.ENABLE_SECONDARY_DISPLAY, SettingsFile.FILE_NAME_CONFIG)
        changeSecondaryOrientation(layoutOption)
    }

    fun disableSecondaryDisplay() {
        BooleanSetting.ENABLE_SECONDARY_DISPLAY.boolean = false
        settings.saveSetting(BooleanSetting.ENABLE_SECONDARY_DISPLAY, SettingsFile.FILE_NAME_CONFIG)
    }

    fun changeActivityOrientation(orientationOption: Int) {
        val activity = context as? Activity ?: return
        IntSetting.ORIENTATION_OPTION.int = orientationOption
        settings.saveSetting(IntSetting.ORIENTATION_OPTION, SettingsFile.FILE_NAME_CONFIG)
        activity.requestedOrientation = orientationOption
    }

    fun toggleScreenUpright() {
        val uprightBoolean = BooleanSetting.UPRIGHT_SCREEN.boolean
        BooleanSetting.UPRIGHT_SCREEN.boolean = !uprightBoolean
        settings.saveSetting(BooleanSetting.UPRIGHT_SCREEN, SettingsFile.FILE_NAME_CONFIG)
        NativeLibrary.reloadSettings()
        NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode())
    }
}
