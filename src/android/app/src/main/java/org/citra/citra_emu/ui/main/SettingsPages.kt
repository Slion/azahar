// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import net.slions.compose.preference.PreferencePage
import org.citra.citra_emu.R
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.features.settings.model.view.SubmenuSetting
import org.citra.citra_emu.features.settings.ui.SettingsListActions
import org.citra.citra_emu.features.settings.ui.SettingsListBuilder

/**
 * The top-level pages of the settings screen: one page per section (General, System,
 * Camera, Gamepad, Graphics, Layout, Network, Audio, Debug, each built by its own
 * SettingsPage*.kt file), the Theme page, and "Reset to Default" as an action row. The
 * section pages carry the same rows the legacy settings list rendered, reading and
 * writing the shared [Settings] model; persistence stays with the host activity.
 *
 * @param settings The loaded settings model.
 * @param gameId The game the settings apply to, if any.
 * @param themeValues The current theme values of the Theme page, applied live by the host.
 * @param onThemeValuesChange Persists new theme values (and syncs the night mode).
 * @param refresh Bumped when the tree must be rebuilt (reset, a binding changed, ...).
 * @param onSettingChanged Called after a row wrote a value.
 * @param onReset Shows the reset-to-defaults confirmation dialog.
 */
@Composable
fun settingsPages(
    settings: Settings,
    gameId: String,
    themeValues: ThemeValues,
    onThemeValuesChange: (ThemeValues) -> Unit,
    refresh: MutableState<Int>,
    onSettingChanged: () -> Unit,
    onReset: () -> Unit,
): List<PreferencePage> {
    val activity = LocalContext.current as Activity
    val actions = remember(activity, refresh) {
        SettingsActions(activity, refresh)
    }
    // A changed setting only mutates the static model: mark it dirty for the save-on-finish
    // and bump the refresh so the pages rebuild and the controlled widgets show the new value.
    actions.onSettingsChanged = {
        onSettingChanged()
        refresh.value++
    }
    val builder = remember(activity, settings) {
        SettingsListBuilder(activity, settings, gameId, actions).also { it.prepare() }
    }
    val theme = themePage(themeValues, onThemeValuesChange)
    return remember(builder, refresh.value, theme) {
        settingsSectionPages(activity, builder, actions, refresh, theme, onReset)
    }
}

/**
 * Wraps the rows of a settings section as a page of the tree. The section's submenu items
 * become nested [PreferencePage.subPages] pages, so the recursion covers the layout
 * subpages (custom layouts, performance overlay, ...).
 */
internal fun settingsPage(
    activity: Activity,
    builder: SettingsListBuilder,
    actions: SettingsListActions,
    refresh: MutableState<Int>,
    sectionId: String,
    title: String,
    icon: @Composable () -> Unit,
): PreferencePage {
    val items = builder.build(sectionId)
    return PreferencePage(
        id = sectionId,
        title = title,
        icon = icon,
        subPages = items.filterIsInstance<SubmenuSetting>().map {
            settingsSubmenuPage(activity, builder, actions, refresh, it)
        },
        content = {
            items.forEachIndexed { index, item ->
                renderSettingsItem(index, item, activity, builder, actions, refresh)
            }
        },
    )
}

/** A submenu item of a section, as a nested page of its parent section. */
private fun settingsSubmenuPage(
    activity: Activity,
    builder: SettingsListBuilder,
    actions: SettingsListActions,
    refresh: MutableState<Int>,
    sub: SubmenuSetting,
): PreferencePage {
    val items = builder.build(sub.menuKey)
    return PreferencePage(
        id = sub.menuKey,
        title = activity.getString(sub.nameId),
        icon = drawableIcon(sub.iconId),
        subPages = items.filterIsInstance<SubmenuSetting>().map {
            settingsSubmenuPage(activity, builder, actions, refresh, it)
        },
        content = {
            items.forEachIndexed { index, item ->
                renderSettingsItem(index, item, activity, builder, actions, refresh)
            }
        },
    )
}

/**
 * The top-level pages of the settings screen: one page per section, the Theme page, and
 * "Reset to Default" as an action row — an onClick page, so tapping it shows the
 * confirmation dialog instead of navigating to a detail.
 */
internal fun settingsSectionPages(
    activity: Activity,
    builder: SettingsListBuilder,
    actions: SettingsListActions,
    refresh: MutableState<Int>,
    themePage: PreferencePage,
    onReset: () -> Unit,
): List<PreferencePage> =
    listOf(
        settingsPageGeneral(activity, builder, actions, refresh),
        settingsPageSystem(activity, builder, actions, refresh),
        settingsPageCamera(activity, builder, actions, refresh),
        settingsPageControls(activity, builder, actions, refresh),
        settingsPageGraphics(activity, builder, actions, refresh),
        settingsPageLayout(activity, builder, actions, refresh),
        settingsPageNetwork(activity, builder, actions, refresh),
        settingsPageAudio(activity, builder, actions, refresh),
        settingsPageDebug(activity, builder, actions, refresh),
        themePage,
        PreferencePage(
            id = "settings_reset",
            title = activity.getString(R.string.reset_to_default),
            icon = { Icon(imageVector = Icons.Filled.Restore, contentDescription = null) },
            onClick = onReset,
            content = {},
        ),
    )