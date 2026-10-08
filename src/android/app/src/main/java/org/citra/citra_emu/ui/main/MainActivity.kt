// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.widget.doOnTextChanged
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import net.slions.compose.preference.PreferencePage
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.HomeNavigationDirections
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import org.citra.citra_emu.activities.EmulationActivity
import org.citra.citra_emu.contracts.OpenFileResultContract
import org.citra.citra_emu.databinding.ActivityMainBinding
import org.citra.citra_emu.databinding.DialogSoftwareKeyboardBinding
import org.citra.citra_emu.dialogs.NetPlayDialog
import org.citra.citra_emu.features.settings.SettingKeys
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.features.settings.model.SettingsViewModel
import org.citra.citra_emu.features.settings.ui.SettingsActivity
import org.citra.citra_emu.fragments.GrantMissingFilesystemPermissionFragment
import org.citra.citra_emu.fragments.SelectUserDirectoryDialogFragment
import org.citra.citra_emu.fragments.UpdateUserDirectoryDialogFragment
import org.citra.citra_emu.model.Game
import org.citra.citra_emu.utils.BuildUtil
import org.citra.citra_emu.utils.CiaInstallWorker
import org.citra.citra_emu.utils.CitraDirectoryHelper
import org.citra.citra_emu.utils.CitraDirectoryUtils
import org.citra.citra_emu.utils.DirectoryInitialization
import org.citra.citra_emu.utils.FileBrowserHelper
import org.citra.citra_emu.utils.GameHelper
import org.citra.citra_emu.utils.GpuDriverHelper
import org.citra.citra_emu.utils.Log
import org.citra.citra_emu.utils.PermissionsHandler
import org.citra.citra_emu.utils.RefreshRateUtil
import org.citra.citra_emu.utils.ThemeUtil
import org.citra.citra_emu.ui.main.ThemeSettings.toNightMode
import org.citra.citra_emu.viewmodel.DriverViewModel
import org.citra.citra_emu.viewmodel.HomeViewModel

class MainActivity :
    AppCompatActivity(),
    ThemeProvider {
    private lateinit var binding: ActivityMainBinding

    private val homeViewModel: HomeViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val driverViewModel: DriverViewModel by viewModels()

    private lateinit var navController: NavController

    // Whether one of the fragment screens (game list, search, …) is shown over the Compose
    // home screen; Compose-observable so the home screen's back handling steps aside.
    private val fragmentScreenVisible = mutableStateOf(false)
    private var fragmentBackCallback: OnBackPressedCallback? = null

    // The Compose theme values, persisted in the dedicated theme preferences file.
    private val themeValues = mutableStateOf(ThemeSettings.load(CitraApplication.appContext))

    override var themeId: Int = 0

    companion object {
        const val KEY_SETUP_CURRENT_PAGE = "SetupCurrentPage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // When a game is already running, reveal it instead of building a new game list on top.
        // super.onCreate() must run before finish(); the normal path below calls it late (after
        // the splash screen), so it cannot be hoisted to the top.
        if (savedInstanceState == null && tryResumeRunningGame()) {
            super.onCreate(savedInstanceState)
            // EmulationActivity sits below this activity in the same task, so finishing reveals
            // and resumes it via onRestart/onResume. Starting it instead would trigger its
            // onNewIntent handler, which stops the running emulation.
            finish()
            return
        }

        RefreshRateUtil.enforceRefreshRate(this)

        val splashScreen = installSplashScreen()
        CitraDirectoryUtils.attemptAutomaticUpdateDirectory()
        splashScreen.setKeepOnScreenCondition {
            !DirectoryInitialization.areCitraDirectoriesReady() &&
                PermissionsHandler.hasWriteAccess(this) &&
                !CitraDirectoryUtils.needToUpdateManually()
        }

        if (PermissionsHandler.hasWriteAccess(applicationContext) &&
            DirectoryInitialization.areCitraDirectoriesReady() &&
            !CitraDirectoryUtils.needToUpdateManually()
        ) {
            settingsViewModel.settings.loadSettings()
        }

        ThemeUtil.themeChangeListener(this)
        ThemeUtil.setTheme(this)
        super.onCreate(savedInstanceState)
        // The contrast preference owns the night mode. It is applied as the process-wide
        // default rather than a per-activity local mode, which does not survive the
        // recreation it triggers and would recreate the activity on every launch.
        AppCompatDelegate.setDefaultNightMode(themeValues.value.themeMode.toNightMode())
        NativeLibrary.initMultiplayer()

        binding = ActivityMainBinding.inflate(layoutInflater)
        val composeView = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setContent {
                MainScreen(
                    pages = homePages(),
                    themeValues = themeValues.value,
                    backEnabled = !fragmentScreenVisible.value,
                    onBack = { finish() },
                )
            }
        }
        // Insert the Compose host behind the fragment container, so a fragment screen shown
        // over the home screen covers it entirely.
        binding.root.addView(composeView, 0)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        window.statusBarColor =
            ContextCompat.getColor(applicationContext, android.R.color.transparent)
        window.navigationBarColor =
            ContextCompat.getColor(applicationContext, android.R.color.transparent)

        navController =
            (supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment)
                .navController
        // Keep the overlay back callback in sync with the fragment back stack, re-evaluated
        // on every stack change so it never acts on a stale value.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navController.currentBackStack.collect { stack ->
                    val canPop = stack.count { it.destination !is NavGraph } > 1
                    fragmentBackCallback?.isEnabled =
                        fragmentScreenVisible.value && !canPop
                }
            }
        }
        setUpNavigation(savedInstanceState)

        lifecycleScope.apply {
            launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    homeViewModel.isPickingUserDir.collect { checkUserPermissions() }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Save the user's current game state.
        outState.putInt(KEY_SETUP_CURRENT_PAGE, homeViewModel.setupCurrentPage)

        // Always call the superclass so it can save the view hierarchy state.
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        checkUserPermissions()

        ThemeUtil.setCorrectTheme(this)
        super.onResume()
    }

    /** True when a game is running and this launch should finish to reveal it. */
    private fun tryResumeRunningGame(): Boolean {
        if (!EmulationActivity.isRunning()) {
            return false
        }
        // Only react to a real relaunch (launcher icon or an external view intent).
        val action = intent?.action
        if (action != Intent.ACTION_MAIN && action != Intent.ACTION_VIEW) {
            return false
        }
        Log.info("Revealing the running game instead of showing the game list")
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun displayMultiplayerDialog() {
        val dialog = NetPlayDialog(this)
        dialog.show()
    }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)
        themeId = resId
    }

    private fun checkUserPermissions() {
        val firstTimeSetup = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(Settings.PREF_FIRST_APP_LAUNCH, true)

        if (firstTimeSetup) {
            return
        }

        if (!BuildUtil.isGooglePlayBuild) {
            fun requestMissingFilesystemPermission() =
                GrantMissingFilesystemPermissionFragment.newInstance()
                    .show(supportFragmentManager, GrantMissingFilesystemPermissionFragment.TAG)

            if (supportFragmentManager.findFragmentByTag(
                    GrantMissingFilesystemPermissionFragment.TAG
                ) ==
                null
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        requestMissingFilesystemPermission()
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestMissingFilesystemPermission()
                    }
                }
            }
        }

        if (homeViewModel.isPickingUserDir.value) {
            return
        }

        if (!PermissionsHandler.hasWriteAccess(this)) {
            SelectUserDirectoryDialogFragment.newInstance(this)
                .show(supportFragmentManager, SelectUserDirectoryDialogFragment.TAG)
            return
        } else if (CitraDirectoryUtils.needToUpdateManually()) {
            UpdateUserDirectoryDialogFragment.newInstance(this)
                .show(supportFragmentManager, UpdateUserDirectoryDialogFragment.TAG)
            return
        }

        if (!BuildUtil.isGooglePlayBuild) {
            if (supportFragmentManager.findFragmentByTag(SelectUserDirectoryDialogFragment.TAG) ==
                null
            ) {
                if (NativeLibrary.getUserDirectory() == "") {
                    SelectUserDirectoryDialogFragment.newInstance(this)
                        .show(supportFragmentManager, SelectUserDirectoryDialogFragment.TAG)
                }
            }
        }
    }

    fun finishSetup(navController: NavController) {
        navController.navigate(R.id.action_firstTimeSetupFragment_to_gamesFragment)
    }

    /**
     * Runs the (fragment-based) first-time setup flow over the home screen on a fresh
     * install; afterwards the nav host simply sits ready behind it.
     */
    private fun setUpNavigation(savedInstanceState: Bundle?) {
        val firstTimeSetup = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean(Settings.PREF_FIRST_APP_LAUNCH, true)

        if (savedInstanceState == null && firstTimeSetup && !homeViewModel.navigatedToSetup) {
            homeViewModel.setupCurrentPage = savedInstanceState?.getInt(KEY_SETUP_CURRENT_PAGE) ?: 0
            navController.navigate(R.id.firstTimeSetupFragment)
            homeViewModel.navigatedToSetup = true
            showFragmentScreen()
        }
    }

    /** Shows the fragment screen at [destinationId] over the home screen. */
    private fun openFragmentScreen(destinationId: Int) {
        if (navController.currentDestination?.id != destinationId) {
            // A fragment screen sits at the bottom of the nav stack: swap it in instead of
            // pushing another level on top, so back always returns to the home screen.
            // The synchronous commit applies the swap before the container is revealed,
            // so the previous screen never flashes.
            navController.popBackStack()
            navController.navigate(destinationId)
            supportFragmentManager.executePendingTransactions()
        }
        showFragmentScreen()
    }

    private fun showFragmentScreen() {
        // Re-added on each show so this callback is checked before the navigation
        // controller's and the Compose home screen's back handlers; it acts only while
        // the fragment screen is up and its own back stack cannot pop.
        fragmentBackCallback?.remove()
        val callback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    hideFragmentScreen()
                }
            }
        fragmentBackCallback = callback
        callback.isEnabled = !navController.canPopBack
        onBackPressedDispatcher.addCallback(this, callback)

        binding.fragmentContainer.visibility = View.VISIBLE
        fragmentScreenVisible.value = true
    }

    private fun hideFragmentScreen() {
        binding.fragmentContainer.visibility = View.GONE
        fragmentScreenVisible.value = false
        fragmentBackCallback?.isEnabled = false
    }

    /**
     * Launches a game through the nav host so the activity-destination bookkeeping applies:
     * when EmulationActivity finishes, the stack pops back to the previous screen.
     */
    private fun launchEmulation(game: Game) {
        navController.navigate(HomeNavigationDirections.actionGlobalEmulationActivity(game))
    }

    /**
     * Whether the fragment screen's own back stack can pop one level. The graph itself is a
     * back-stack entry, so only actual destinations count.
     */
    private val NavController.canPopBack: Boolean
        get() = currentBackStack.value.count { it.destination !is NavGraph } > 1

    private fun showArticBaseDialog() {
        val inputBinding = DialogSoftwareKeyboardBinding.inflate(LayoutInflater.from(this))
        var textInputValue: String = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getString(SettingKeys.last_artic_base_addr(), "")!!

        inputBinding.editTextInput.setText(textInputValue)
        inputBinding.editTextInput.doOnTextChanged { text, _, _, _ ->
            textInputValue = text.toString()
        }

        MaterialAlertDialogBuilder(this)
            .setView(inputBinding.root)
            .setTitle(getString(R.string.artic_base_enter_address))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (textInputValue.isNotEmpty()) {
                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                        .edit()
                        .putString(SettingKeys.last_artic_base_addr(), textInputValue)
                        .apply()
                    launchEmulation(
                        Game(
                            title = getString(R.string.artic_base),
                            path = "articbase://$textInputValue",
                            filename = ""
                        )
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareLog() {
        val logDirectory =
            DocumentFile.fromTreeUri(this, PermissionsHandler.citraDirectory)?.findFile("log")
        val currentLog = logDirectory?.findFile("azahar_log.txt")
        val oldLog = logDirectory?.findFile("azahar_log.old.txt")

        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
        }
        if (!Log.gameLaunched && oldLog?.exists() == true) {
            intent.putExtra(Intent.EXTRA_STREAM, oldLog.uri)
            startActivity(Intent.createChooser(intent, getText(R.string.share_log)))
        } else if (currentLog?.exists() == true) {
            intent.putExtra(Intent.EXTRA_STREAM, currentLog.uri)
            startActivity(Intent.createChooser(intent, getText(R.string.share_log)))
        } else {
            Toast.makeText(this, getText(R.string.share_log_not_found), Toast.LENGTH_SHORT)
                .show()
        }
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

    /** Confirms and runs the settings reset from the home screen. */
    private fun showResetSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_all_settings)
            .setMessage(R.string.reset_all_settings_description)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                SettingsActivity.resetSettings()
                Toast.makeText(this, R.string.settings_reset, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The root pages of the home screen (Settings, Theme, Options, Search, Applications). */
    @Composable
    private fun homePages(): List<PreferencePage> {
        val userDir by homeViewModel.userDir.collectAsStateWithLifecycle()
        val gamesDir by homeViewModel.gamesDir.collectAsStateWithLifecycle()
        // The DriverViewModel cannot load before the user directory has been picked.
        val setupDone = userDir?.isNotEmpty() == true &&
            !PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Settings.PREF_FIRST_APP_LAUNCH, true)
        val driverName by produceState<String?>(initialValue = null) {
            if (!setupDone) return@produceState
            driverViewModel.selectedDriverMetadata.collect { value = it }
        }
        val settingsTitle = stringResource(R.string.preferences_settings)
        val resetTitle = stringResource(R.string.reset_to_default)
        val themePage = themePage(themeValues.value) { onThemeValuesChange(it) }
        return remember(userDir, gamesDir, driverName, themeValues.value) {
            val driverSupported = GpuDriverHelper.supportsCustomDriverLoading()
            buildHomePages(
                settingsSections =
                    listOf(
                        SettingsSectionEntry(
                            Settings.SECTION_CORE,
                            getString(R.string.preferences_general),
                            Icons.Filled.Settings,
                        ),
                        SettingsSectionEntry(
                            Settings.SECTION_SYSTEM,
                            getString(R.string.preferences_system),
                            Icons.Filled.DevicesOther,
                        ),
                        SettingsSectionEntry(
                            Settings.SECTION_CAMERA,
                            getString(R.string.preferences_camera),
                            Icons.Filled.PhotoCamera,
                        ),
                        SettingsSectionEntry(
                            Settings.SECTION_CONTROLS,
                            getString(R.string.preferences_controls),
                            Icons.Filled.Gamepad,
                        ),
                        SettingsSectionEntry(
                            Settings.SECTION_RENDERER,
                            getString(R.string.preferences_graphics),
                            Icons.Filled.GraphicEq,
                        ),
                        SettingsSectionEntry(
                            Settings.SECTION_LAYOUT,
                            getString(R.string.preferences_layout),
                            Icons.Filled.FitScreen,
                        ),
                        SettingsSectionEntry(
                            Settings.SECTION_NETWORK,
                            getString(R.string.preferences_network),
                            Icons.Filled.Lan,
                        ),
                        SettingsSectionEntry(
                            Settings.SECTION_AUDIO,
                            getString(R.string.preferences_audio),
                            Icons.Filled.AudioFile,
                        ),
                        SettingsSectionEntry(
                            Settings.SECTION_DEBUG,
                            getString(R.string.preferences_debug),
                            Icons.Filled.BugReport,
                        ),
                    ),
                onOpenSettingsSection = { section ->
                    SettingsActivity.launch(this, section, "")
                },
                onResetSettings = { showResetSettingsDialog() },
                themePage = themePage,
                settingsTitle = settingsTitle,
                resetTitle = resetTitle,
                optionsTitle = getString(R.string.home_options),
                searchTitle = getString(R.string.home_search),
                applicationsTitle = getString(R.string.home_games),
                options =
                    listOf(
                        OptionRow(
                            id = "artic_base",
                            title = getString(R.string.artic_base_connect),
                            summary = getString(R.string.artic_base_connect_description),
                            icon = Icons.Filled.Router,
                            onClick = { showArticBaseDialog() },
                        ),
                        OptionRow(
                            id = "multiplayer",
                            title = getString(R.string.multiplayer),
                            summary = getString(R.string.multiplayer_description),
                            icon = Icons.Filled.Groups,
                            onClick = { displayMultiplayerDialog() },
                        ),
                        OptionRow(
                            id = "install_game_content",
                            title = getString(R.string.install_game_content),
                            summary = getString(R.string.install_game_content_description),
                            icon = Icons.Filled.Download,
                            onClick = { ciaFileInstaller.launch(true) },
                        ),
                        OptionRow(
                            id = "system_files",
                            title = getString(R.string.setup_system_files),
                            summary = getString(R.string.setup_system_files_description),
                            icon = Icons.Filled.SystemUpdate,
                            onClick = { openFragmentScreen(R.id.systemFilesFragment) },
                        ),
                        OptionRow(
                            id = "share_log",
                            title = getString(R.string.share_log),
                            summary = getString(R.string.share_log_description),
                            icon = Icons.Filled.Share,
                            onClick = { shareLog() },
                        ),
                        OptionRow(
                            id = "driver_manager",
                            title = getString(R.string.gpu_driver_manager),
                            summary =
                                if (driverSupported) {
                                    driverName
                                        ?: getString(R.string.system_gpu_driver)
                                } else {
                                    getString(R.string.custom_driver_not_supported)
                                },
                            icon = Icons.Filled.Memory,
                            enabled = driverSupported,
                            onClick = { openFragmentScreen(R.id.driverManagerFragment) },
                        ),
                        OptionRow(
                            id = "user_folder",
                            title = getString(R.string.select_citra_user_folder),
                            summary = getString(R.string.select_citra_user_folder_home_description),
                            icon = Icons.Filled.Home,
                            onClick = {
                                PermissionsHandler.compatibleSelectDirectory(openCitraDirectory)
                            },
                        ),
                        OptionRow(
                            id = "games_folder",
                            title = getString(R.string.select_games_folder),
                            summary =
                                if (gamesDir.isEmpty()) {
                                    getString(R.string.select_games_folder_description)
                                } else {
                                    gamesDir
                                },
                            icon = Icons.Filled.Folder,
                            onClick = { getGamesDirectory.launch(null) },
                        ),
                        OptionRow(
                            id = "about",
                            title = getString(R.string.about),
                            summary = getString(R.string.about_description),
                            icon = Icons.Filled.Info,
                            onClick = { openFragmentScreen(R.id.aboutFragment) },
                        ),
                    ),
                onSearch = { openFragmentScreen(R.id.searchFragment) },
                onApplications = { openFragmentScreen(R.id.gamesFragment) },
            )
        }
    }

    private fun createOpenCitraDirectoryLauncher(
        permissionsLost: Boolean
    ): ActivityResultLauncher<Uri?> {
        return registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { result: Uri? ->
            if (result == null) {
                return@registerForActivityResult
            }

            if (!BuildUtil.isGooglePlayBuild) {
                if (NativeLibrary.getNativePath(result) == "") {
                    SelectUserDirectoryDialogFragment.newInstance(
                        this,
                        R.string.invalid_selection,
                        R.string.invalid_user_directory
                    ).show(supportFragmentManager, SelectUserDirectoryDialogFragment.TAG)
                    return@registerForActivityResult
                }
            }

            CitraDirectoryHelper(this@MainActivity, permissionsLost)
                .showCitraDirectoryDialog(result, buttonState = {})
        }
    }

    val openCitraDirectory = createOpenCitraDirectoryLauncher(permissionsLost = false)
    val openCitraDirectoryLostPermission = createOpenCitraDirectoryLauncher(permissionsLost = true)

    val ciaFileInstaller = registerForActivityResult(
        OpenFileResultContract()
    ) { result: Intent? ->
        if (result == null) {
            return@registerForActivityResult
        }

        val selectedFiles =
            FileBrowserHelper.getSelectedFiles(result, applicationContext, listOf("cia", "zcia"))
        if (selectedFiles == null) {
            Toast.makeText(applicationContext, R.string.cia_file_not_found, Toast.LENGTH_LONG)
                .show()
            return@registerForActivityResult
        }

        val workManager = WorkManager.getInstance(applicationContext)
        workManager.enqueueUniqueWork(
            "installCiaWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequest.Builder(CiaInstallWorker::class.java)
                .setInputData(
                    Data.Builder().putStringArray("CIA_FILES", selectedFiles)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        )
    }

    val setupOpenCitraDirectory = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { result: Uri? ->
        homeViewModel.selectedCitraDirectory = result
    }

    val setupGetGamesDirectory = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { result: Uri? ->
        homeViewModel.selectedGamesDirectory = result
    }

    private val getGamesDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
            if (result == null) {
                return@registerForActivityResult
            }

            contentResolver.takePersistableUriPermission(
                result,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // When a new directory is picked, we currently will reset the existing games
            // database. This effectively means that only one game directory is supported.
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .edit()
                .putString(GameHelper.KEY_GAME_PATH, result.toString())
                .apply()

            Toast.makeText(this, R.string.games_dir_selected, Toast.LENGTH_LONG).show()

            homeViewModel.setGamesDir(this, result.path!!)
        }
}
