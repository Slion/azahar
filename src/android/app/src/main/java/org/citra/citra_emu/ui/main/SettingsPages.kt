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
import net.slions.compose.toolkit.CardStyle
import net.slions.compose.toolkit.Page
import net.slions.compose.toolkit.group
import net.slions.compose.toolkit.item
import net.slions.compose.toolkit.section
import org.citra.citra_emu.R
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.features.settings.model.view.HeaderSetting
import org.citra.citra_emu.features.settings.model.view.SettingsItem
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
): Page {
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
        Page(id = "settings", title = activity.getString(R.string.preferences_settings)) {
            group(key = "settings_group", style = CardStyle.Filled) {
                item(page = settingsPageGeneral(activity, builder, actions, refresh))
                item(page = settingsPageSystem(activity, builder, actions, refresh))
                item(page = settingsPageCamera(activity, builder, actions, refresh))
                item(page = settingsPageControls(activity, builder, actions, refresh))
                item(page = settingsPageGraphics(activity, builder, actions, refresh))
                item(page = settingsPageLayout(activity, builder, actions, refresh))
                item(page = settingsPageNetwork(activity, builder, actions, refresh))
                item(page = settingsPageAudio(activity, builder, actions, refresh))
                item(page = settingsPageDebug(activity, builder, actions, refresh))
                item(page = theme)
                // An action row, not a page: a plain item with a click, so it shows
                // neither a page-row chevron nor a two-target separator (there is a
                // single action).
                item(
                    title = activity.getString(R.string.reset_to_default),
                    icon = { Icon(imageVector = Icons.Filled.Restore, contentDescription = null) },
                    onClick = onReset,
                )
            }
        }
    }
}

/**
 * Wraps the rows of a settings section as a page of the tree: the section headers become
 * [section]s, the rows between them join one filled card [group] (the submenu items as
 * nested pages — an [item] with a [Page] at their position in the list), so the recursion
 * covers the layout subpages (custom layouts, performance overlay, ...).
 */
internal fun settingsPage(
    activity: Activity,
    builder: SettingsListBuilder,
    actions: SettingsListActions,
    refresh: MutableState<Int>,
    sectionId: String,
    title: String,
    icon: @Composable () -> Unit,
): Page {
    val items = builder.build(sectionId)
    return Page(id = sectionId, title = title, icon = icon) {
        var pending = emptyList<Pair<Int, SettingsItem>>()
        var groupNumber = 0
        fun flushGroup() {
            if (pending.isEmpty()) {
                return
            }
            group(key = "${sectionId}_group_$groupNumber", style = CardStyle.Filled) {
                pending.forEach { (index, setting) ->
                    if (setting is SubmenuSetting) {
                        item(page = settingsSubmenuPage(activity, builder, actions, refresh, setting))
                    } else {
                        renderSettingsItem(index, setting, activity, builder, actions, refresh)
                    }
                }
            }
            groupNumber++
            pending = emptyList()
        }
        items.forEachIndexed { index, setting ->
            if (setting is HeaderSetting) {
                flushGroup()
                section(key = "${sectionId}_$index", title = activity.getString(setting.nameId))
            } else {
                pending += index to setting
            }
        }
        flushGroup()
    }
}

/** A submenu item of a section, as a nested page of its parent section. */
private fun settingsSubmenuPage(
    activity: Activity,
    builder: SettingsListBuilder,
    actions: SettingsListActions,
    refresh: MutableState<Int>,
    sub: SubmenuSetting,
): Page {
    val items = builder.build(sub.menuKey)
    return Page(
        id = sub.menuKey,
        title = activity.getString(sub.nameId),
        icon = drawableIcon(sub.iconId),
    ) {
        var pending = emptyList<Pair<Int, SettingsItem>>()
        var groupNumber = 0
        fun flushGroup() {
            if (pending.isEmpty()) {
                return
            }
            group(key = "${sub.menuKey}_group_$groupNumber", style = CardStyle.Filled) {
                pending.forEach { (index, setting) ->
                    if (setting is SubmenuSetting) {
                        item(page = settingsSubmenuPage(activity, builder, actions, refresh, setting))
                    } else {
                        renderSettingsItem(index, setting, activity, builder, actions, refresh)
                    }
                }
            }
            groupNumber++
            pending = emptyList()
        }
        items.forEachIndexed { index, setting ->
            if (setting is HeaderSetting) {
                flushGroup()
                section(key = "${sub.menuKey}_$index", title = activity.getString(setting.nameId))
            } else {
                pending += index to setting
            }
        }
        flushGroup()
    }
}
