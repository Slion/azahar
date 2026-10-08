// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.features.settings.model.Settings

/**
 * The Material3 theme of the Compose screens.
 *
 * The activity's AppCompat theme (chosen by [org.citra.citra_emu.utils.ThemeUtil] from the
 * theme preferences) already carries the full set of Material3 color roles, so the scheme is
 * read from the resolved theme attributes; in Material You mode the dynamic scheme is used
 * instead. Theme preference changes recreate the activity, so the scheme only needs to be
 * re-derived when the effective night mode changes within a composition.
 */
@Composable
fun AzaharTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dark = isNightMode(context)
    val colorScheme =
        remember(dark) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext)
                    .getBoolean(Settings.PREF_MATERIAL_YOU, false)
            ) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                colorSchemeFromTheme(view, dark)
            }
        }
    MaterialTheme(colorScheme = colorScheme) { content() }
}

private fun isNightMode(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

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
