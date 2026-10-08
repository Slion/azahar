// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import net.slions.compose.preference.AccentColorOption
import net.slions.compose.preference.ColorPreference
import net.slions.compose.preference.ListPreference
import net.slions.compose.preference.LiveSliderPreference
import net.slions.compose.preference.Preference
import net.slions.compose.preference.PreferencePage
import net.slions.compose.preference.PreferencePageScreen
import net.slions.compose.preference.ProvidePreferenceLocals
import net.slions.compose.preference.ProvidePreferenceTheme
import net.slions.compose.preference.preference
import net.slions.compose.preference.preferenceCategory
import net.slions.compose.preference.preferenceCardGroup
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

/** A section of the settings tree, shown as a card of the Settings page. */
data class SettingsSectionEntry(
    val section: String,
    val title: String,
    val icon: ImageVector,
)

/**
 * The root pages of the home screen: Settings (a card group over the settings tree) and
 * Theme (live theme controls), then Options (a page of rows, each wired to the screen or
 * dialog it opens), then Search and Applications as action rows that open the existing
 * fragment screens over the home screen instead of navigating within it.
 */
fun buildHomePages(
    settingsSections: List<SettingsSectionEntry>,
    onOpenSettingsSection: (String) -> Unit,
    onResetSettings: () -> Unit,
    themePage: PreferencePage,
    settingsTitle: String,
    resetTitle: String,
    optionsTitle: String,
    searchTitle: String,
    applicationsTitle: String,
    options: List<OptionRow>,
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
            content = {
                preferenceCardGroup(key = "settings_sections") {
                    settingsSections.forEach { entry ->
                        card(title = entry.title) {
                            Preference(
                                title = entry.title,
                                icon = icon(entry.icon),
                                onClick = { onOpenSettingsSection(entry.section) },
                            )
                        }
                    }
                }
                preference(
                    key = "settings_reset",
                    title = resetTitle,
                    icon = icon(Icons.Filled.Restore),
                    onClick = onResetSettings,
                )
            },
        ),
        themePage,
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

/**
 * The "Theme" page: live theme controls. Changing a row re-themes the Compose UI
 * immediately; the values are persisted in the dedicated theme preferences file.
 */
@Composable
fun themePage(
    values: ThemeValues,
    onValuesChange: (ThemeValues) -> Unit,
): PreferencePage {
    val dark = effectiveDarkTheme(values.themeMode, isSystemInDarkTheme())
    val defaultAccent = systemDefaultAccentColor(dark)
    // Row text resolved up front: the page's content lambda is a plain LazyListScope
    // builder, where stringResource cannot be called.
    val colorsTitle = stringResource(R.string.theme_colors)
    val contrastTitle = stringResource(R.string.theme_contrast)
    val colorTitle = stringResource(R.string.theme_color)
    val tintTitle = stringResource(R.string.theme_tint)
    val textsTitle = stringResource(R.string.theme_texts)
    val fontTitle = stringResource(R.string.theme_font)
    val fontSizeTitle = stringResource(R.string.theme_font_size)
    val shapesTitle = stringResource(R.string.theme_shapes)
    val cornerTitle = stringResource(R.string.theme_corner)
    return PreferencePage(
        id = "theme",
        title = stringResource(R.string.home_theme),
        icon = { Icon(imageVector = Icons.Filled.Palette, contentDescription = null) },
    ) {
        preferenceCategory(key = "theme_colors_category", title = colorsTitle)
        preferenceCardGroup(key = "theme_colors_group") {
            card(title = contrastTitle, summary = values.themeMode.label) {
                ListPreference(
                    value = values.themeMode,
                    onValueChange = { onValuesChange(values.copy(themeMode = it)) },
                    values = ThemeMode.entries,
                    title = contrastTitle,
                    summary = values.themeMode.label,
                    icon = { Icon(imageVector = Icons.Filled.Contrast, contentDescription = null) },
                    valueToText = { AnnotatedString(it.label) },
                )
            }
            card(title = colorTitle, summary = accentNameOf(values.accent)) {
                ColorPreference(
                    value = values.accent ?: "",
                    onValueChange = { onValuesChange(values.copy(accent = it.ifEmpty { null })) },
                    options = ThemeValues.ACCENT_PRESETS.map { AccentColorOption(it.name, it.hex) },
                    title = colorTitle,
                    summary = accentNameOf(values.accent),
                    icon = { Icon(imageVector = Icons.Filled.ColorLens, contentDescription = null) },
                    defaultOptionColor = defaultAccent,
                )
            }
            card(title = tintTitle) {
                LiveSliderPreference(
                    title = tintTitle,
                    value = (values.tintFactorPercent ?: DEFAULT_TINT_FACTOR_PERCENT).toFloat(),
                    onValueChange = { onValuesChange(values.copy(tintFactorPercent = it.toInt())) },
                    valueRange = TINT_FACTOR_RANGE.first.toFloat()..TINT_FACTOR_RANGE.last.toFloat(),
                    // M3 'steps' counts intermediate stops (segments = steps + 1), so for 5%
                    // steps over 0..50 (10 segments) we pass 9, not 10.
                    valueSteps = (TINT_FACTOR_RANGE.last - TINT_FACTOR_RANGE.first) / 5 - 1,
                    valueText = { "${it.toInt()}%" },
                    live = true,
                    icon = { Icon(imageVector = Icons.Filled.Opacity, contentDescription = null) },
                )
            }
        }

        preferenceCategory(key = "theme_text_category", title = textsTitle)
        preferenceCardGroup(key = "theme_text_group") {
            card(title = fontTitle, summary = fontLabel(values.fontFamily)) {
                ListPreference(
                    value = values.fontFamily ?: ThemeValues.DEFAULT_FONT,
                    onValueChange = { onValuesChange(values.copy(fontFamily = it)) },
                    values = ThemeValues.FONT_FAMILIES,
                    title = fontTitle,
                    summary = fontLabel(values.fontFamily),
                    icon = { Icon(imageVector = Icons.Filled.TextFields, contentDescription = null) },
                    valueToText = { AnnotatedString(fontLabel(it)) },
                )
            }
            card(title = fontSizeTitle) {
                LiveSliderPreference(
                    title = fontSizeTitle,
                    value = (values.fontSizePercent ?: DEFAULT_FONT_SIZE_PERCENT).toFloat(),
                    onValueChange = { onValuesChange(values.copy(fontSizePercent = it.toInt())) },
                    valueRange = 80f..140f,
                    // 5% steps over 80..140 = 12 segments, so 11 intermediate stops.
                    valueSteps = (140 - 80) / 5 - 1,
                    valueText = { "${it.toInt()}%" },
                    live = true,
                    icon = { Icon(imageVector = Icons.Filled.FormatSize, contentDescription = null) },
                )
            }
        }

        preferenceCategory(key = "theme_shapes_category", title = shapesTitle)
        preferenceCardGroup(key = "theme_shapes_group") {
            card(title = cornerTitle) {
                LiveSliderPreference(
                    title = cornerTitle,
                    value = (values.cornerRadiusDp ?: DEFAULT_CORNER_RADIUS_DP).toFloat(),
                    onValueChange = { onValuesChange(values.copy(cornerRadiusDp = it.toInt())) },
                    valueRange = 0f..32f,
                    // 2 dp steps over 0..32 = 16 segments, so 15 intermediate stops.
                    valueSteps = (32 - 0) / 2 - 1,
                    valueText = { "${it.toInt()} dp" },
                    live = true,
                    icon = { Icon(imageVector = Icons.Filled.RoundedCorner, contentDescription = null) },
                )
            }
        }
    }
}

private fun accentNameOf(hex: String?): String =
    ThemeValues.ACCENT_PRESETS.firstOrNull { it.hex == hex }?.name
        ?: ThemeValues.ACCENT_PRESETS.first().name

private fun fontLabel(font: String?): String = when (font) {
    ThemeValues.SERIF_FONT -> "Serif"
    ThemeValues.MONO_FONT -> "Monospace"
    else -> "Default"
}

/**
 * The default accent's resolved color (dynamic colors on Android 12+), or null where
 * unavailable — the swatch of the "Default" accent option.
 */
@Composable
private fun systemDefaultAccentColor(dark: Boolean): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context).primary
    else dynamicLightColorScheme(context).primary
}
