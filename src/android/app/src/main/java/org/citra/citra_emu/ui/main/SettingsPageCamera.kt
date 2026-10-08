// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.runtime.MutableState
import net.slions.compose.preference.PreferencePage
import org.citra.citra_emu.R
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.features.settings.ui.SettingsListActions
import org.citra.citra_emu.features.settings.ui.SettingsListBuilder

/** The "Camera" page of the settings tree: the camera settings. */
internal fun settingsPageCamera(
    activity: Activity,
    builder: SettingsListBuilder,
    actions: SettingsListActions,
    refresh: MutableState<Int>,
): PreferencePage =
    settingsPage(
        activity,
        builder,
        actions,
        refresh,
        Settings.SECTION_CAMERA,
        activity.getString(R.string.preferences_camera),
        { Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = null) },
    )
