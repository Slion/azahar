// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import net.slions.compose.toolkit.Catalog
import net.slions.compose.toolkit.CardStyle
import net.slions.compose.toolkit.Page
import net.slions.compose.toolkit.ProvidePreferenceLocals
import net.slions.compose.toolkit.ProvidePreferenceTheme
import net.slions.compose.toolkit.group
import net.slions.compose.toolkit.item
import org.citra.citra_emu.R

/**
 * The home screen: the adaptive preference screen hosting the home [root] page (Games,
 * Options, Settings), themed from the user's [ThemeValues].
 *
 * @param root The root page whose items are the list pane's top level.
 * @param themeValues The current theme values, applied live by [AzaharTheme].
 * @param backEnabled False while a fragment screen is shown over the home screen, so that
 * system back goes to that screen (and its back stack) first.
 * @param onBack Called when the user backs out of the root page itself.
 */
@Composable
fun MainScreen(
    root: Page,
    themeValues: ThemeValues,
    backEnabled: Boolean,
    onBack: () -> Unit,
) {
    ProvidePreferenceLocals {
        AzaharTheme(themeValues) {
            ProvidePreferenceTheme {
                Catalog(
                    title = stringResource(R.string.app_name),
                    root = root,
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
 * The root page of the home screen: Games, Options, and Settings as a group of filled
 * cards. Games is a page of the catalog (searchable); Options holds rows wired to their
 * screens or dialogs; Settings launches the settings activity.
 */
fun buildHomeRootPage(
    title: String,
    settingsTitle: String,
    settingsSummary: String,
    optionsTitle: String,
    options: List<OptionRow>,
    gamesPage: Page,
    onSettings: () -> Unit,
): Page {
    fun icon(vector: ImageVector) =
        @Composable { Icon(imageVector = vector, contentDescription = null) }

    return Page(id = "home", title = title) {
        group(style = CardStyle.Filled) {
            item(page = gamesPage)
            item(
                page =
                    Page(
                        id = "options",
                        title = optionsTitle,
                        icon = icon(Icons.Filled.Tune),
                    ) {
                        options.forEach { row ->
                            item(
                                key = row.id,
                                title = row.title,
                                summary = row.summary,
                                enabled = row.enabled,
                                icon = icon(row.icon),
                                onClick = if (row.enabled) row.onClick else null,
                            )
                        }
                    },
            )
            item(
                title = settingsTitle,
                summary = settingsSummary,
                icon = icon(Icons.Filled.Settings),
                actionIcon = icon(Icons.AutoMirrored.Filled.OpenInNew),
                onClick = onSettings,
            )
        }
    }
}
