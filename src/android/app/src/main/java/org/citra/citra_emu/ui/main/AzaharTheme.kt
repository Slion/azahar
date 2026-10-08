// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.features.settings.model.Settings

/**
 * The Material3 theme of the Compose screens, driven by the user's [ThemeValues].
 *
 * The color scheme starts from the app's usual resolution (the dynamic scheme in Material
 * You mode, otherwise the resolved attributes of the activity's AppCompat theme) and a
 * fixed accent or a non-default neutral tint re-derives it. Shapes and typography follow
 * the corner-radius, font-size and font-family values. The effective dark theme is
 * resolved from [ThemeValues.themeMode], falling back to the current system setting.
 */
@Composable
fun AzaharTheme(values: ThemeValues, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dark = effectiveDarkTheme(values.themeMode, isNightMode(context))
    val colorScheme =
        remember(context, view, dark, values.accent, values.tintFactorPercent) {
            azaharColorScheme(context, view, values, dark)
        }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = themeShapes(values.cornerRadiusDp ?: DEFAULT_CORNER_RADIUS_DP),
        typography = themeTypography(values.fontSizePercent ?: DEFAULT_FONT_SIZE_PERCENT, values.fontFamily),
    ) { content() }
}

/** Resolves the effective dark-theme flag for [mode]; [ThemeMode.SYSTEM] follows the system. */
fun effectiveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean =
    when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

private fun isNightMode(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

/** The color scheme for [values]: a fixed accent, the dynamic scheme, or the app theme. */
private fun azaharColorScheme(
    context: Context,
    view: View,
    values: ThemeValues,
    dark: Boolean,
): ColorScheme {
    val tintFactor = (values.tintFactorPercent ?: DEFAULT_TINT_FACTOR_PERCENT) / 100f
    val seedArgb = values.accent?.let { hex -> runCatching { android.graphics.Color.parseColor(hex) }.getOrNull() }
    return when {
        seedArgb != null -> seededScheme(seedArgb, dark, tintFactor)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext)
                .getBoolean(Settings.PREF_MATERIAL_YOU, false) -> {
            val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // The neutral-tint slider is a no-op on the raw dynamic scheme; when it moves
            // off its default, re-derive the scheme from the dynamic primary. At the
            // default the pure dynamic colors are kept, so "Default" looks native.
            if (tintFactor == DEFAULT_TINT_FACTOR_PERCENT / 100f) {
                dynamic
            } else {
                seededScheme(dynamic.primary.toArgb(), dark, tintFactor)
            }
        }
        else -> {
            val base = colorSchemeFromTheme(view, dark)
            if (tintFactor == DEFAULT_TINT_FACTOR_PERCENT / 100f) {
                base
            } else {
                seededScheme(base.primary.toArgb(), dark, tintFactor)
            }
        }
    }
}

/** Shape scale built on the user's corner radius (the "medium" size of the scale). */
private fun themeShapes(cornerRadiusDp: Int): Shapes =
    Shapes(
        extraSmall = RoundedCornerShape((cornerRadiusDp / 3).dp),
        small = RoundedCornerShape((cornerRadiusDp / 2).dp),
        medium = RoundedCornerShape(cornerRadiusDp.dp),
        large = RoundedCornerShape((cornerRadiusDp * 1.5f).dp),
        extraLarge = RoundedCornerShape((cornerRadiusDp * 2.5f).dp),
    )

/** The [fontFamily] type scale, every style scaled by [percent] (clamped to 80..140). */
private fun themeTypography(percent: Int, fontFamily: String?): Typography {
    val family =
        when (fontFamily) {
            ThemeValues.SERIF_FONT -> FontFamily.Serif
            ThemeValues.MONO_FONT -> FontFamily.Monospace
            else -> FontFamily.Default
        }
    val factor = percent.coerceIn(80, 140) / 100f
    // Scale the font size and set the family; the M3 type scale's proportional line-height
    // and letter-spacing stay correct relative to the new size.
    fun TextStyle.scale(): TextStyle =
        copy(fontSize = (fontSize.value * factor).sp, fontFamily = family)
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.scale(),
        displayMedium = base.displayMedium.scale(),
        displaySmall = base.displaySmall.scale(),
        headlineLarge = base.headlineLarge.scale(),
        headlineMedium = base.headlineMedium.scale(),
        headlineSmall = base.headlineSmall.scale(),
        titleLarge = base.titleLarge.scale(),
        titleMedium = base.titleMedium.scale(),
        titleSmall = base.titleSmall.scale(),
        bodyLarge = base.bodyLarge.scale(),
        bodyMedium = base.bodyMedium.scale(),
        bodySmall = base.bodySmall.scale(),
        labelLarge = base.labelLarge.scale(),
        labelMedium = base.labelMedium.scale(),
        labelSmall = base.labelSmall.scale(),
    )
}

/**
 * A full M3 [ColorScheme] derived from a seed color at [tintFactor] neutral tint.
 *
 * The M3 tonal palette assumes a vivid seed. With a *dark* seed the dark-mode tone 80
 * lands near-black and vanishes against the dark surface, so the primary's lightness is
 * floored (hue and chroma kept).
 */
private fun seededScheme(seed: Int, dark: Boolean, tintFactor: Float): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val hct = FloatArray(3)
    ColorUtils.colorToM3HCT(seed, hct)
    val hue = hct[0]
    val chroma = hct[1]
    fun t(tone: Int) = Color(ColorUtils.M3HCTToColor(hue, chroma, tone.toFloat()))
    // Neutral roles: the seed's hue at [tintFactor] of its chroma (Material tints its
    // dynamic neutral palette at 0.05; a higher factor pushes the accent into the
    // neutrals).
    fun n(tone: Int) = Color(ColorUtils.M3HCTToColor(hue, chroma * tintFactor, tone.toFloat()))
    val primary =
        if (dark) {
            val hct2 = FloatArray(3)
            ColorUtils.colorToM3HCT(ColorUtils.M3HCTToColor(hue, chroma, 80f), hct2)
            Color(ColorUtils.M3HCTToColor(hct2[0], hct2[1], hct2[2].coerceAtLeast(0.45f)))
        } else {
            t(40)
        }
    return base.copy(
        primary = primary,
        onPrimary = if (dark) t(20) else t(100),
        primaryContainer = if (dark) t(30) else t(90),
        onPrimaryContainer = if (dark) t(90) else t(10),
        inversePrimary = t(40),
        secondary = if (dark) t(80) else t(40),
        onSecondary = if (dark) t(20) else t(100),
        secondaryContainer = if (dark) t(30) else t(90),
        onSecondaryContainer = if (dark) t(90) else t(10),
        tertiary = if (dark) t(80) else t(40),
        onTertiary = if (dark) t(20) else t(100),
        tertiaryContainer = if (dark) t(30) else t(90),
        onTertiaryContainer = if (dark) t(90) else t(10),
        surfaceTint = primary,
        // Neutral family, at the standard M3 tones for the given theme.
        background = if (dark) n(10) else n(98),
        onBackground = if (dark) n(90) else n(10),
        surface = if (dark) n(6) else n(98),
        onSurface = if (dark) n(90) else n(10),
        surfaceVariant = if (dark) n(30) else n(90),
        onSurfaceVariant = if (dark) n(80) else n(30),
        outline = if (dark) n(60) else n(50),
        outlineVariant = if (dark) n(30) else n(80),
        inverseSurface = if (dark) n(90) else n(20),
        inverseOnSurface = if (dark) n(20) else n(95),
        surfaceContainerLowest = if (dark) n(4) else n(100),
        surfaceContainerLow = if (dark) n(10) else n(96),
        surfaceContainer = if (dark) n(12) else n(94),
        surfaceContainerHigh = if (dark) n(17) else n(92),
        surfaceContainerHighest = if (dark) n(22) else n(90),
    )
}

/** Derives a [ColorScheme] from the Material3 color roles resolved by [view]'s theme. */
private fun colorSchemeFromTheme(view: View, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    fun color(attr: Int): Color = Color(MaterialColors.getColor(view, attr))
    return base.copy(
        primary = color(com.google.android.material.R.attr.colorPrimary),
        onPrimary = color(com.google.android.material.R.attr.colorOnPrimary),
        primaryContainer = color(com.google.android.material.R.attr.colorPrimaryContainer),
        onPrimaryContainer = color(com.google.android.material.R.attr.colorOnPrimaryContainer),
        secondary = color(com.google.android.material.R.attr.colorSecondary),
        onSecondary = color(com.google.android.material.R.attr.colorOnSecondary),
        secondaryContainer = color(com.google.android.material.R.attr.colorSecondaryContainer),
        onSecondaryContainer = color(com.google.android.material.R.attr.colorOnSecondaryContainer),
        tertiary = color(com.google.android.material.R.attr.colorTertiary),
        onTertiary = color(com.google.android.material.R.attr.colorOnTertiary),
        tertiaryContainer = color(com.google.android.material.R.attr.colorTertiaryContainer),
        onTertiaryContainer = color(com.google.android.material.R.attr.colorOnTertiaryContainer),
        background = color(android.R.attr.colorBackground),
        onBackground = color(com.google.android.material.R.attr.colorOnBackground),
        surface = color(com.google.android.material.R.attr.colorSurface),
        onSurface = color(com.google.android.material.R.attr.colorOnSurface),
        surfaceVariant = color(com.google.android.material.R.attr.colorSurfaceVariant),
        onSurfaceVariant = color(com.google.android.material.R.attr.colorOnSurfaceVariant),
        error = color(com.google.android.material.R.attr.colorError),
        onError = color(com.google.android.material.R.attr.colorOnError),
        errorContainer = color(com.google.android.material.R.attr.colorErrorContainer),
        onErrorContainer = color(com.google.android.material.R.attr.colorOnErrorContainer),
        outline = color(com.google.android.material.R.attr.colorOutline),
        inverseOnSurface = color(com.google.android.material.R.attr.colorOnSurfaceInverse),
        inverseSurface = color(com.google.android.material.R.attr.colorSurfaceInverse),
        inversePrimary = color(com.google.android.material.R.attr.colorPrimaryInverse),
    )
}
