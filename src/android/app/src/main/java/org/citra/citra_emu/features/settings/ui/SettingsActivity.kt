// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.features.settings.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import net.slions.compose.preference.PreferencePageScreen
import net.slions.compose.preference.ProvidePreferenceLocals
import net.slions.compose.preference.ProvidePreferenceTheme
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import org.citra.citra_emu.features.settings.model.BooleanSetting
import org.citra.citra_emu.features.settings.model.FloatSetting
import org.citra.citra_emu.features.settings.model.IntSetting
import org.citra.citra_emu.features.settings.model.ScaledFloatSetting
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.features.settings.model.SettingsViewModel
import org.citra.citra_emu.features.settings.model.StringSetting
import org.citra.citra_emu.features.settings.utils.SettingsFile
import org.citra.citra_emu.ui.main.AzaharTheme
import org.citra.citra_emu.ui.main.ThemeSettings
import org.citra.citra_emu.ui.main.ThemeSettings.toNightMode
import org.citra.citra_emu.ui.main.ThemeValues
import org.citra.citra_emu.ui.main.settingsPages
import org.citra.citra_emu.utils.DirectoryInitialization
import org.citra.citra_emu.utils.FileUtil
import org.citra.citra_emu.utils.Log
import org.citra.citra_emu.utils.PermissionsHandler
import org.citra.citra_emu.utils.RefreshRateUtil
import org.citra.citra_emu.utils.SystemSaveGame
import org.citra.citra_emu.utils.ThemeUtil
import org.citra.citra_emu.utils.TurboHelper

/**
 * The settings screen: the adaptive preference pages tree (each section a nested
 * [net.slions.compose.preference.PreferencePage.subPages] page), hosted in its own activity
 * so the home screen and the settings tree have separate search scopes.
 */
class SettingsActivity :
    AppCompatActivity(),
    SettingsActivityView {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val themeValues = mutableStateOf(ThemeSettings.load(CitraApplication.appContext))

    // The config file is written on finish, only when a setting changed.
    private var settingsDirty = false
    private val settingsRefresh = mutableStateOf(0)

    override val settings: Settings get() = settingsViewModel.settings

    override fun onCreate(savedInstanceState: Bundle?) {
        RefreshRateUtil.enforceRefreshRate(this)

        ThemeUtil.setTheme(this)

        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (!DirectoryInitialization.areCitraDirectoriesReady()) {
            DirectoryInitialization.start()
        }
        settingsDirty = savedInstanceState?.getBoolean(KEY_SETTINGS_DIRTY) == true

        val gameId = intent.getStringExtra(ARG_GAME_ID).orEmpty()
        if (!settings.isLoaded) {
            if (gameId.isEmpty()) {
                settings.loadSettings(this)
            } else {
                settings.loadSettings(gameId, this)
            }
        }

        setContentView(
            ComposeView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setContent {
                    ProvidePreferenceLocals {
                        AzaharTheme(themeValues.value) {
                            ProvidePreferenceTheme {
                                PreferencePageScreen(
                                    title = stringResource(R.string.preferences_settings),
                                    pages =
                                        settingsPages(
                                            settings = settings,
                                            gameId = gameId,
                                            themeValues = themeValues.value,
                                            onThemeValuesChange = { onThemeValuesChange(it) },
                                            refresh = settingsRefresh,
                                            onSettingChanged = { settingsDirty = true },
                                            onReset = { showResetSettingsDialog() },
                                        ),
                                    onBack = { finish() },
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    override fun onResume() {
        // The settings tree reads the native system save game (username, country, …), which
        // is only bound to the core's CFG module after this call.
        SystemSaveGame.load()
        super.onResume()
    }

    override fun onPause() {
        SystemSaveGame.save()
        super.onPause()
    }

    // The theme can recreate this activity (contrast change); the dirty flag must survive so
    // the config file is still written on finish.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SETTINGS_DIRTY, settingsDirty)
    }

    /**
     * If this is called, the user has left the settings screen (potentially through the
     * home button) and will expect their changes to be persisted.
     */
    override fun onStop() {
        super.onStop()
        if (isFinishing && settingsDirty) {
            Log.debug("[SettingsActivity] Settings activity stopping. Saving settings to INI...")
            settingsDirty = false
            settings.saveSettings(this)
            // added to ensure that layout changes take effect as soon as settings window closes
            NativeLibrary.reloadSettings()
            NativeLibrary.updateFramebuffer(NativeLibrary.isPortraitMode())
            updateAndroidImageVisibility()
            TurboHelper.reloadTurbo(false) // TODO: Can this go somewhere else? -OS
        }
        NativeLibrary.reloadSettings()
    }

    override fun showSettingsFragment(menuTag: String, addToStack: Boolean, gameId: String) = Unit
    override fun onSettingsFileLoaded() = Unit
    override fun onSettingsFileNotFound() = Unit

    override fun showToastMessage(message: String, isLong: Boolean) {
        Toast.makeText(
            this,
            message,
            if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    override fun onSettingChanged() {
        settingsDirty = true
    }

    /** Persists the theme values and syncs the night mode when the contrast changes. */
    private fun onThemeValuesChange(values: ThemeValues) {
        if (themeValues.value == values) {
            return
        }
        if (themeValues.value.themeMode != values.themeMode) {
            AppCompatDelegate.setDefaultNightMode(values.themeMode.toNightMode())
        }
        themeValues.value = values
        ThemeSettings.save(CitraApplication.appContext, values)
    }

    /** Confirms and runs the settings reset to defaults. */
    private fun showResetSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_all_settings)
            .setMessage(R.string.reset_all_settings_description)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                SettingsActivity.resetSettings()
                showToastMessage(getString(R.string.settings_reset), true)
                settingsDirty = false
                settingsRefresh.value++
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateAndroidImageVisibility() {
        val dataDirTreeUri: Uri
        val dataDirDocument: DocumentFile
        val nomediaFileDocument: DocumentFile?
        val nomediaFileExists: Boolean
        try {
            dataDirTreeUri = PermissionsHandler.citraDirectory
            dataDirDocument =
                DocumentFile.fromTreeUri(CitraApplication.appContext, dataDirTreeUri)!!
            nomediaFileDocument = dataDirDocument.findFile(".nomedia")
            nomediaFileExists = (nomediaFileDocument != null)
        } catch (e: Exception) {
            Log.error(
                "[SettingsActivity]: Error occurred while trying to find .nomedia, error: " +
                    e.message
            )
            return
        }

        if (BooleanSetting.ANDROID_HIDE_IMAGES.boolean) {
            if (!nomediaFileExists) {
                Log.info("[SettingsActivity]: Attempting to create .nomedia in user data directory")
                FileUtil.createFile(dataDirTreeUri.toString(), ".nomedia")
            }
        } else if (nomediaFileExists) {
            Log.info("[SettingsActivity]: Attempting to delete .nomedia in user data directory")
            nomediaFileDocument!!.delete()
        }
    }

    companion object {
        private const val ARG_MENU_TAG = "menu_tag"
        private const val ARG_GAME_ID = "game_id"
        private const val KEY_SETTINGS_DIRTY = "settings_dirty"

        @JvmStatic
        fun launch(context: Context, menuTag: String?, gameId: String?) {
            val settings = Intent(context, SettingsActivity::class.java)
            settings.putExtra(ARG_MENU_TAG, menuTag)
            settings.putExtra(ARG_GAME_ID, gameId)
            context.startActivity(settings)
        }

        /**
         * Resets the settings to their defaults: clears the controller keys and the static
         * memory of each setting, deletes and recreates the config file, and restores the
         * default system values.
         */
        @JvmStatic
        fun resetSettings() {
            val controllerKeys = Settings.buttonKeys + Settings.circlePadKeys + Settings.cStickKeys +
                Settings.dPadAxisKeys + Settings.dPadButtonKeys + Settings.triggerKeys
            val editor =
                PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext).edit()
            controllerKeys.forEach { editor.remove(it) }
            editor.apply()

            // Reset the static memory representation of each setting
            BooleanSetting.clear()
            FloatSetting.clear()
            ScaledFloatSetting.clear()
            IntSetting.clear()
            StringSetting.clear()

            // Delete settings file because the user may have changed values that do not exist in the UI
            val settingsFile = SettingsFile.getSettingsFile(SettingsFile.FILE_NAME_CONFIG)
            if (!settingsFile.delete()) {
                throw IOException("Failed to delete $settingsFile")
            }

            // Set the root of the document tree before we create a new config file or the native code
            // will fail when creating the file.
            if (DirectoryInitialization.setCitraUserDirectory()) {
                CitraApplication.documentsTree.setRoot(Uri.parse(DirectoryInitialization.userPath))
                NativeLibrary.createConfigFile()
            } else {
                throw IllegalStateException("Azahar directory unavailable when accessing config file!")
            }

            // Set default values for system config file
            SystemSaveGame.apply {
                setUsername("AZAHAR")
                setBirthday(11, 7)
                setSystemLanguage(1)
                setSoundOutputMode(1)
                setCountryCode(49)
                setPlayCoins(42)
            }
        }
    }
}
