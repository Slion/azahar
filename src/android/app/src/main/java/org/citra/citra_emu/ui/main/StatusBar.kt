// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The status bar uses the transient (hide-on-swipe) mode only when the display's height is
 * below this many dp: on a short edge it is a large share of the screen, so hiding it buys
 * usable area. On taller screens it stays a normal, static bar. Tuned per device class
 * rather than exposed as a user setting.
 */
const val TRANSIENT_STATUS_BAR_MAX_HEIGHT_DP = 480

/**
 * Applies the height-gated status bar mode to [activity]'s window: on a short screen
 * (height below [TRANSIENT_STATUS_BAR_MAX_HEIGHT_DP]) the status bar uses the same
 * transparent transient mode as the game — hidden by default, and a swipe from the top
 * brings it back as an overlay that folds back on its own; on taller screens it is left
 * as a normal, static bar. The host activity recreates on rotation, so this only needs
 * to run in [Activity.onCreate].
 */
fun Activity.applyStatusBarVisibility() {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    val heightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
    if (heightDp < TRANSIENT_STATUS_BAR_MAX_HEIGHT_DP) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
    }
}
