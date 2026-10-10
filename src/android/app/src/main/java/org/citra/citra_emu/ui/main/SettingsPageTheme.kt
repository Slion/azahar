// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import net.slions.compose.toolkit.ItemList
import net.slions.compose.toolkit.Page
import net.slions.compose.toolkit.group
import net.slions.compose.toolkit.section
import org.citra.citra_emu.R

/**
 * The "Theme" page of the settings screen: live theme controls. Changing a row re-themes the
 * Compose UI immediately; the values are persisted in the dedicated theme preferences file.
 */
@Composable
internal fun themePage(
    values: ThemeValues,
    onValuesChange: (ThemeValues) -> Unit,
): Page {
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
    return Page(
        id = "theme",
        title = stringResource(R.string.home_theme),
        icon = { Icon(imageVector = Icons.Filled.Palette, contentDescription = null) },
    ) {
        section(key = "theme_colors_category", title = colorsTitle)
        group(key = "theme_colors_group") {
            item(title = contrastTitle, summary = values.themeMode.label) {
                ItemList(
                    value = values.themeMode,
                    onValueChange = { onValuesChange(values.copy(themeMode = it)) },
                    values = ThemeMode.entries,
                    title = contrastTitle,
                    summary = values.themeMode.label,
                    icon = { Icon(imageVector = Icons.Filled.Contrast, contentDescription = null) },
                    valueToText = { AnnotatedString(it.label) },
                )
            }
            item(title = colorTitle, summary = accentNameOf(values.accent)) {
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
            item(title = tintTitle) {
                LiveItemSlider(
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

        section(key = "theme_text_category", title = textsTitle)
        group(key = "theme_text_group") {
            item(title = fontTitle, summary = fontLabel(values.fontFamily)) {
                ItemList(
                    value = values.fontFamily ?: ThemeValues.DEFAULT_FONT,
                    onValueChange = { onValuesChange(values.copy(fontFamily = it)) },
                    values = ThemeValues.FONT_FAMILIES,
                    title = fontTitle,
                    summary = fontLabel(values.fontFamily),
                    icon = { Icon(imageVector = Icons.Filled.TextFields, contentDescription = null) },
                    valueToText = { AnnotatedString(fontLabel(it)) },
                )
            }
            item(title = fontSizeTitle) {
                LiveItemSlider(
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

        section(key = "theme_shapes_category", title = shapesTitle)
        group(key = "theme_shapes_group") {
            item(title = cornerTitle) {
                LiveItemSlider(
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
