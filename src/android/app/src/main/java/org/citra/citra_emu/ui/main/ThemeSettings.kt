// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persistence for the [ThemeValues] of the Compose UI.
 *
 * The values live in a dedicated preferences file, separate from the app's default
 * preferences: the Compose theme must never read or write the keys the rest of the app
 * (or an official azahar install sharing the same user folder) relies on.
 */
object ThemeSettings {
    private const val FILE_NAME = "azalea_theme"

    private const val KEY_THEME_MODE = "theme.mode"
    private const val KEY_ACCENT = "theme.accent"
    private const val KEY_CORNER_RADIUS = "theme.cornerRadius"
    private const val KEY_FONT_SIZE = "theme.fontSize"
    private const val KEY_TINT_FACTOR = "theme.tintFactor"
    private const val KEY_FONT_FAMILY = "theme.fontFamily"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Loads the stored values; unset or invalid entries fall back to their defaults. */
    fun load(context: Context): ThemeValues {
        val prefs = prefs(context)
        return ThemeValues(
            themeMode = prefs.getString(KEY_THEME_MODE, null)?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: ThemeMode.SYSTEM,
            accent = prefs.getString(KEY_ACCENT, null),
            cornerRadiusDp = prefs.getInt(KEY_CORNER_RADIUS, -1).takeIf { it >= 0 },
            fontSizePercent = prefs.getInt(KEY_FONT_SIZE, -1).takeIf { it in 80..140 },
            tintFactorPercent =
                prefs.getInt(KEY_TINT_FACTOR, -1).takeIf { it in TINT_FACTOR_RANGE },
            fontFamily = prefs.getString(KEY_FONT_FAMILY, null)?.takeIf {
                it in ThemeValues.FONT_FAMILIES
            },
        )
    }

    /** Stores the values (unset entries are removed, so the file stays minimal). */
    fun save(context: Context, values: ThemeValues) {
        with(prefs(context).edit()) {
            putString(KEY_THEME_MODE, values.themeMode.name)
            if (values.accent != null) putString(KEY_ACCENT, values.accent)
            else remove(KEY_ACCENT)
            if (values.cornerRadiusDp != null) putInt(KEY_CORNER_RADIUS, values.cornerRadiusDp)
            else remove(KEY_CORNER_RADIUS)
            if (values.fontSizePercent != null) putInt(KEY_FONT_SIZE, values.fontSizePercent)
            else remove(KEY_FONT_SIZE)
            if (values.tintFactorPercent != null) putInt(KEY_TINT_FACTOR, values.tintFactorPercent)
            else remove(KEY_TINT_FACTOR)
            if (values.fontFamily != null) putString(KEY_FONT_FAMILY, values.fontFamily)
            else remove(KEY_FONT_FAMILY)
            apply()
        }
    }

    /** The AppCompat night mode matching [mode]. */
    fun ThemeMode.toNightMode(): Int =
        when (this) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
}
