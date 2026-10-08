// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import net.slions.compose.preference.PreferencePage
import net.slions.compose.preference.PreferencePageScreen
import net.slions.compose.preference.ProvidePreferenceLocals
import net.slions.compose.preference.ProvidePreferenceTheme
import net.slions.compose.preference.preference
import org.citra.citra_emu.R

/**
 * The home screen: the adaptive preference screen hosting the root pages (Settings, Theme,
 * Options, Search, Applications), themed from the user's [ThemeValues].
 *
 * @param pages The root pages, in display order.
 * @param themeValues The current theme values, applied live by [AzaharTheme].
 * @param backEnabled False while a fragment screen is shown over the home screen, so that
 * system back goes to that screen (and its back stack) first.
 * @param onBack Called when the user backs out of the root page itself.
 */
@Composable
fun MainScreen(
    pages: List<PreferencePage>,
    themeValues: ThemeValues,
    backEnabled: Boolean,
    onBack: () -> Unit,
) {
    ProvidePreferenceLocals {
        AzaharTheme(themeValues) {
            ProvidePreferenceTheme {
                PreferencePageScreen(
                    title = stringResource(R.string.app_name),
                    pages = pages,
                    onBack = onBack,
                    backEnabled = backEnabled,
                )
            }
        }
    }
}

/** A row of the Options page. */
data class OptionRow(
    val id: String,
    val title: String,
    val summary: String?,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The root pages of the home screen: Settings (a row that launches the settings activity,
 * so it owns its own search scope), then Options (a page of rows, each wired to the screen
 * or dialog it opens), then Search and Applications as action rows that open the existing
 * fragment screens over the home screen instead of navigating within it.
 */
fun buildHomePages(
    settingsTitle: String,
    optionsTitle: String,
    searchTitle: String,
    applicationsTitle: String,
    options: List<OptionRow>,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onApplications: () -> Unit,
): List<PreferencePage> {
    fun icon(vector: ImageVector) =
        @Composable { Icon(imageVector = vector, contentDescription = null) }

    return listOf(
        PreferencePage(
            id = "settings",
            title = settingsTitle,
            icon = icon(Icons.Filled.Settings),
            onClick = onSettings,
            content = {},
        ),
        PreferencePage(
            id = "options",
            title = optionsTitle,
            icon = icon(Icons.Filled.Tune),
            content = {
                options.forEach { row ->
                    preference(
                        key = row.id,
                        title = row.title,
                        summary = row.summary,
                        enabled = row.enabled,
                        icon = icon(row.icon),
                        onClick = if (row.enabled) row.onClick else null,
                    )
                }
            },
        ),
        PreferencePage(
            id = "search",
            title = searchTitle,
            icon = icon(Icons.Filled.Search),
            onClick = onSearch,
            content = {},
        ),
        PreferencePage(
            id = "applications",
            title = applicationsTitle,
            icon = icon(Icons.Filled.VideogameAsset),
            onClick = onApplications,
            content = {},
        ),
    )
}
