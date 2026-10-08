// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import androidx.compose.runtime.Immutable

/**
 * The user-selectable theme properties of the Compose UI, as edited on the Theme page.
 *
 * All properties are optional: a null value means "use the built-in default".
 *
 * @param accent hex color string (e.g. `"#6750A4"`) used as the color-scheme seed; null =
 *   the default colors (dynamic on Android 12+ when Material You is enabled, otherwise the
 *   app theme's palette).
 * @param cornerRadiusDp corner-radius scale for cards/rows (the "medium" size of the scale);
 *   null = [DEFAULT_CORNER_RADIUS_DP].
 * @param fontSizePercent font-size scale as a percentage of the default type scale; null =
 *   [DEFAULT_FONT_SIZE_PERCENT].
 * @param tintFactorPercent how strongly the accent tints the neutral roles (background,
 *   surfaces, text), as a percentage of the seed's chroma; null =
 *   [DEFAULT_TINT_FACTOR_PERCENT].
 * @param fontFamily one of [FONT_FAMILIES]; null = [DEFAULT_FONT].
 */
@Immutable
data class ThemeValues(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: String? = null,
    val cornerRadiusDp: Int? = null,
    val fontSizePercent: Int? = null,
    val tintFactorPercent: Int? = null,
    val fontFamily: String? = null,
) {
    companion object {
        /** The platform default font. */
        const val DEFAULT_FONT = "default"

        /** A serif font. */
        const val SERIF_FONT = "serif"

        /** A monospace font. */
        const val MONO_FONT = "monospace"

        /** All selectable font families, in display order. */
        val FONT_FAMILIES: List<String> = listOf(DEFAULT_FONT, SERIF_FONT, MONO_FONT)

        /**
         * The accent presets, in display order (default first). The first entry, whose [hex]
         * is null, means the default colors; the rest are fixed seeds — darker pastels
         * rather than fully saturated colors, so the derived scheme stays readable in both
         * themes.
         */
        val ACCENT_PRESETS: List<AccentPreset> = listOf(
            AccentPreset("Default", null),
            AccentPreset("Purple", "#6750A4"),
            AccentPreset("Blue", "#6288C0"),
            AccentPreset("Teal", "#4FA39B"),
            AccentPreset("Green", "#7DA86E"),
            AccentPreset("Amber", "#C9A24B"),
            AccentPreset("Red", "#C2685C"),
            AccentPreset("Orange", "#CE8A55"),
            AccentPreset("Pink", "#C57FB5"),
        )
    }
}

/** An accent preset of the Theme page. A null [hex] means the default colors. */
@Immutable
data class AccentPreset(val name: String, val hex: String?)

/** The light/dark theme the app should use. */
enum class ThemeMode(val label: String) {
    /** Follow the system setting. */
    SYSTEM("System"),

    /** Always light. */
    LIGHT("Light"),

    /** Always dark. */
    DARK("Dark"),
}

/** Default corner radius (the "medium" size of the shape scale) in dp. */
const val DEFAULT_CORNER_RADIUS_DP = 12

/** The default font-size scale, as a percentage. */
const val DEFAULT_FONT_SIZE_PERCENT = 100

/**
 * The default neutral-tint factor as a percentage: how strongly the seed hue tints the
 * neutral roles (background, surfaces, text). 5% is the standard M3 dynamic factor; higher
 * values push the accent into the neutrals.
 */
const val DEFAULT_TINT_FACTOR_PERCENT = 5

/** The tint-factor range, in percent, as a slider. */
val TINT_FACTOR_RANGE: IntRange = 0..50
