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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.slions.compose.toolkit.Page
import net.slions.compose.toolkit.item
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.HomeNavigationDirections
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import org.citra.citra_emu.activities.EmulationActivity
import org.citra.citra_emu.contracts.OpenFileResultContract
import org.citra.citra_emu.databinding.ActivityMainBinding
import org.citra.citra_emu.databinding.DialogSoftwareKeyboardBinding
import org.citra.citra_emu.dialogs.NetPlayDialog
import org.citra.citra_emu.features.cheats.ui.CheatsFragmentDirections
import org.citra.citra_emu.features.settings.SettingKeys
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.features.settings.ui.SettingsActivity
import org.citra.citra_emu.features.settings.utils.SettingsFile
import org.citra.citra_emu.fragments.CompressProgressDialogFragment
import org.citra.citra_emu.fragments.GrantMissingFilesystemPermissionFragment
import org.citra.citra_emu.fragments.IndeterminateProgressDialogFragment
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
import org.citra.citra_emu.utils.GameIconUtils
import org.citra.citra_emu.utils.GpuDriverHelper
import org.citra.citra_emu.utils.Log
import org.citra.citra_emu.utils.PermissionsHandler
import org.citra.citra_emu.utils.RefreshRateUtil
import org.citra.citra_emu.utils.ThemeUtil
import org.citra.citra_emu.ui.main.ThemeSettings.toNightMode
import org.citra.citra_emu.viewmodel.CompressProgressDialogViewModel
import org.citra.citra_emu.viewmodel.DriverViewModel
import org.citra.citra_emu.viewmodel.GamesViewModel
import org.citra.citra_emu.viewmodel.HomeViewModel

class MainActivity :
    AppCompatActivity(),
    ThemeProvider {
    private lateinit var binding: ActivityMainBinding

    private val homeViewModel: HomeViewModel by viewModels()
    private val driverViewModel: DriverViewModel by viewModels()
    // Activity-scoped, so it is the same instance GamesFragment observes: the home
    // screen's Games page and the fragment stay in sync.
    private val gamesViewModel: GamesViewModel by viewModels()

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
                    root = homeRootPage(),
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
     * Launches a game tapped in the home screen's Games page: refreshes the library if the
     * file is gone (like the list's row tap) and records the last-played time.
     */
    private fun launchApplication(game: Game) {
        if (!game.isInstalled &&
            !NativeLibrary.nativeFileExists(NativeLibrary.getNativePath(game.path.toUri()))) {
            Toast.makeText(this, R.string.loader_error_file_not_found, Toast.LENGTH_LONG).show()
            gamesViewModel.reloadGames(true)
            return
        }
        PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
            .putLong(game.keyLastPlayedTime, System.currentTimeMillis())
            .apply()
        launchEmulation(game)
    }

    /** Formats a playtime in seconds the way the legacy about-game sheet does. */
    private fun formatPlayTime(seconds: Long): String =
        when {
            seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m ${seconds % 60}s"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }

    /** Localizes the pipe-separated region list, as the legacy list does. */
    private fun translateRegions(regionsString: String): String =
        regionsString
            .split("|")
            .map {
                val res =
                    when (it) {
                        "Japan" -> R.string.japan
                        "North America" -> R.string.north_america
                        "Europe" -> R.string.europe
                        "Australia" -> R.string.australia
                        "China" -> R.string.china
                        "Korea" -> R.string.korea
                        "Taiwan" -> R.string.taiwan
                        "Region free" -> R.string.region_free
                        "Invalid region" -> R.string.invalid_region
                        "" -> R.string.invalid_region
                        else -> {
                            Log.error("[MainActivity] Unrecognized region string \"$it\"")
                            R.string.region_get_error
                        }
                    }
                getString(res)
            }
            .joinToString(", ")

    /** The per-title folders of the installed game, as the legacy list computes them. */
    private data class GameDirs(
        val gameDir: String,
        val saveDir: String,
        val dlcDir: String,
        val updatesDir: String,
        val extraDir: String,
        val appDir: String,
    )

    private fun gameDirectories(game: Game): GameDirs {
        val basePath =
            "sdmc/Nintendo 3DS/00000000000000000000000000000000/00000000000000000000000000000000"
        val titleId = String.format("%016x", game.titleId).lowercase()
        return GameDirs(
            gameDir = game.path.substringBeforeLast("/"),
            saveDir = basePath + "/title/${titleId.substring(0, 8)}/${titleId.substring(8)}/data/00000001",
            dlcDir = basePath + "/title/0004008c/${titleId.substring(8)}/content",
            updatesDir = basePath + "/title/0004000e/${titleId.substring(8)}/content",
            extraDir =
                basePath +
                    "/extdata/00000000/" +
                    String.format("%016X", game.titleId).substring(8, 14).padStart(8, '0'),
            appDir =
                game.path
                    .substringBeforeLast("/")
                    .split("/")
                    .filter { it.isNotEmpty() }
                    .joinToString("/"),
        )
    }

    /** Whether the game's [dir] (relative to the user directory) exists on the device. */
    private fun gameFolderExists(dir: String): Boolean =
        CitraApplication.documentsTree.folderUriHelper(dir)?.let {
            DocumentFile.fromTreeUri(this, it)?.exists()
        } ?: false

    /** The "open folder" entries of the legacy sheet as a single-choice dialog. */
    private fun showOpenFoldersDialog(game: Game) {
        val dirs = gameDirectories(game)
        val entries =
            listOf(
                R.string.game_context_open_app to dirs.appDir,
                R.string.game_context_open_save_dir to dirs.saveDir,
                R.string.game_context_open_updates to dirs.updatesDir,
                R.string.game_context_open_dlc to dirs.dlcDir,
                R.string.game_context_open_extra to dirs.extraDir,
            ).filter { gameFolderExists(it.second) }
        if (entries.isEmpty()) {
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.game_page_open_folders)
            .setItems(entries.map { getString(it.first) }.toTypedArray()) { _, which ->
                val uri =
                    DocumentFile.fromTreeUri(
                        this,
                        CitraApplication.documentsTree.folderUriHelper(entries[which].second)!!,
                    )!!.uri
                val intent =
                    Intent(Intent.ACTION_VIEW)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setType("*/*")
                intent.data = uri
                startActivity(intent)
            }
            .show()
    }

    /** The "uninstall" entries of the legacy sheet as a single-choice dialog. */
    private fun showUninstallDialog(game: Game) {
        val dirs = gameDirectories(game)
        val entries =
            listOf(
                R.string.uninstall_cia to dirs.gameDir,
                R.string.game_context_uninstall_dlc to dirs.dlcDir,
                R.string.game_context_uninstall_updates to dirs.updatesDir,
            ).filter { gameFolderExists(it.second) }
        if (entries.isEmpty()) {
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.game_page_uninstall)
            .setItems(entries.map { getString(it.first) }.toTypedArray()) { _, which ->
                val label = entries[which].first
                IndeterminateProgressDialogFragment
                    .newInstance(
                        this,
                        R.string.uninstalling,
                        false,
                        {
                            when (label) {
                                R.string.uninstall_cia ->
                                    NativeLibrary.uninstallTitle(game.titleId, game.mediaType)
                                R.string.game_context_uninstall_dlc ->
                                    NativeLibrary.uninstallTitle(
                                        game.titleId or 0x8C00000000L,
                                        Game.MediaType.SDMC,
                                    )
                                R.string.game_context_uninstall_updates ->
                                    NativeLibrary.uninstallTitle(
                                        game.titleId or 0xE00000000L,
                                        Game.MediaType.SDMC,
                                    )
                            }
                            gamesViewModel.reloadGames(true)
                        },
                    )
                    .show(supportFragmentManager, IndeterminateProgressDialogFragment.TAG)
            }
            .show()
    }

    /** Deletes the game's shader cache for the chosen backend, as the legacy sheet does. */
    private fun showDeleteCacheDialog(game: Game) {
        val options = arrayOf(getString(R.string.vulkan), getString(R.string.opengles))
        var selectedIndex = -1
        val dialog =
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_cache_select_backend)
                .setSingleChoiceItems(options, -1) { _, which ->
                    selectedIndex = which
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val progToast =
                        Toast.makeText(
                            CitraApplication.appContext,
                            R.string.deleting_shader_cache,
                            Toast.LENGTH_LONG,
                        )
                    progToast.show()

                    lifecycleScope.launch(Dispatchers.IO) {
                        when (selectedIndex) {
                            0 -> NativeLibrary.deleteVulkanShaderCache(game.titleId)
                            1 -> NativeLibrary.deleteOpenGLShaderCache(game.titleId)
                        }

                        runOnUiThread {
                            progToast.cancel()
                            Toast.makeText(
                                CitraApplication.appContext,
                                R.string.shader_cache_deleted,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
                .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false

            val listView = dialog.listView
            listView.setOnItemClickListener {
                _,
                _,
                position,
                _ ->
                    selectedIndex = position
                    positiveButton.isEnabled = true
            }
        }

        dialog.show()
    }

    /**
     * Compresses or decompresses the [game]'s file to [outputUri] (the document picker's
     * target), mirroring the legacy list's compression flow.
     */
    private fun compressGameFile(game: Game, outputUri: Uri, shouldCompress: Boolean) {
        val outputPath =
            if (!BuildUtil.isGooglePlayBuild) {
                "!" + NativeLibrary.getNativePath(outputUri)
            } else {
                outputUri.toString()
            }
        CompressProgressDialogViewModel.reset()
        val dialog = CompressProgressDialogFragment.newInstance(shouldCompress, outputPath)
        dialog.showNow(supportFragmentManager, CompressProgressDialogFragment.TAG)

        lifecycleScope.launch(Dispatchers.IO) {
            val status =
                if (shouldCompress) {
                    NativeLibrary.compressFile(game.path, outputPath)
                } else {
                    NativeLibrary.decompressFile(game.path, outputPath)
                }

            runOnUiThread {
                dialog.dismiss()
                val resId =
                    when (status) {
                        NativeLibrary.CompressStatus.SUCCESS ->
                            if (shouldCompress) {
                                R.string.compress_success
                            } else {
                                R.string.decompress_success
                            }
                        NativeLibrary.CompressStatus.COMPRESS_UNSUPPORTED ->
                            R.string.compress_unsupported
                        NativeLibrary.CompressStatus.COMPRESS_ALREADY_COMPRESSED ->
                            R.string.compress_already
                        NativeLibrary.CompressStatus.COMPRESS_FAILED -> R.string.compress_failed
                        NativeLibrary.CompressStatus.DECOMPRESS_UNSUPPORTED ->
                            R.string.decompress_unsupported
                        NativeLibrary.CompressStatus.DECOMPRESS_NOT_COMPRESSED ->
                            R.string.decompress_not_compressed
                        NativeLibrary.CompressStatus.DECOMPRESS_FAILED ->
                            R.string.decompress_failed
                        NativeLibrary.CompressStatus.INSTALLED_APPLICATION ->
                            R.string.compress_decompress_installed_app
                    }

                MaterialAlertDialogBuilder(this@MainActivity)
                    .setMessage(getString(resId))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()

                gamesViewModel.reloadGames(false)
            }
        }
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

        /** The game's icon (raw 48x48 pixels from the native scan), with the shared fallback. */
    @Composable
    private fun GameIcon(game: Game) {
        val bitmap = remember(game.icon) { GameIconUtils.gameIconBitmap(game) }
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
        } else {
            Image(
                painterResource(R.drawable.no_icon),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
        }
    }

    /** The root page of the home screen (Settings, Options, Search, Applications, Games). */
    @Composable
    private fun homeRootPage(): Page {
        val userDir by homeViewModel.userDir.collectAsStateWithLifecycle()
        val gamesDir by homeViewModel.gamesDir.collectAsStateWithLifecycle()
        val games by gamesViewModel.games.collectAsStateWithLifecycle()
        // The DriverViewModel cannot load before the user directory has been picked.
        val setupDone = userDir?.isNotEmpty() == true &&
            !PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Settings.PREF_FIRST_APP_LAUNCH, true)
        val driverName by produceState<String?>(initialValue = null) {
            if (!setupDone) return@produceState
            driverViewModel.selectedDriverMetadata.collect { value = it }
        }
        // The inserted-cartridge preference is shared with the legacy list; mirror it so
        // the game page's row label updates when it is toggled here.
        var insertedCartridge by remember {
            mutableStateOf(
                PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("insertedCartridge", "") ?: ""
            )
        }
        return remember(userDir, gamesDir, driverName, themeValues.value, games, insertedCartridge) {
            val driverSupported = GpuDriverHelper.supportsCustomDriverLoading()
            // The game list as a catalog page (the Applications row keeps opening the
            // legacy fragment; the two coexist while the transition runs). Each game is a
            // page row whose detail page mirrors the legacy about-game sheet, so the
            // catalog's search indexes and opens every game.
            val gamesPage =
                Page(
                    id = "games",
                    title = getString(R.string.home_games_page),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.VideogameAsset,
                            contentDescription = null,
                        )
                    },
                ) {
                    games.forEach { game ->
                        item(
                            page =
                                Page(
                                    id = "game_${game.titleId}_${game.filename}",
                                    title =
                                        if (game.fileType == "unknown") {
                                            getString(R.string.invalid_rom)
                                        } else {
                                            game.title
                                        },
                                    summary =
                                        listOf(game.company, game.regions)
                                            .filter { it.isNotEmpty() }
                                            .joinToString(" · "),
                                    icon = { GameIcon(game) },
                                ) {
                                    item(
                                        key = "play",
                                        title = getString(R.string.play),
                                        onClick = { launchApplication(game) },
                                    )
                                    if (game.company.isNotEmpty()) {
                                        item(key = "company", title = game.company)
                                    }
                                    if (game.regions.isNotEmpty()) {
                                        item(
                                            key = "region",
                                            title = getString(R.string.game_context_region),
                                            summary = translateRegions(game.regions),
                                        )
                                    }
                                    item(
                                        key = "id",
                                        title = getString(R.string.game_context_id),
                                        summary = String.format("%016X", game.titleId),
                                    )
                                    item(
                                        key = "file",
                                        title = getString(R.string.game_context_file),
                                        summary = game.filename,
                                    )
                                    item(
                                        key = "type",
                                        title = getString(R.string.game_context_type),
                                        summary = game.fileType,
                                    )
                                    item(
                                        key = "playtime",
                                        title = getString(R.string.game_context_playtime),
                                        summary =
                                            formatPlayTime(
                                                NativeLibrary.playTimeManagerGetPlayTime(game.titleId)
                                            ),
                                    )
                                    if (game.isInsertable) {
                                        item(
                                            key = "cartridge",
                                            title =
                                                if (insertedCartridge == game.path) {
                                                    getString(R.string.game_context_eject)
                                                } else {
                                                    getString(R.string.game_context_insert)
                                                },
                                            onClick = {
                                                val inserted =
                                                    if (insertedCartridge == game.path) {
                                                        ""
                                                    } else {
                                                        game.path
                                                    }
                                                insertedCartridge = inserted
                                                PreferenceManager
                                                    .getDefaultSharedPreferences(this@MainActivity)
                                                    .edit()
                                                    .putString("insertedCartridge", inserted)
                                                    .apply()
                                            },
                                        )
                                    }
                                    item(
                                        key = "cheats",
                                        title = getString(R.string.cheats),
                                        onClick = {
                                            navController.popBackStack()
                                            navController.navigate(
                                                CheatsFragmentDirections.actionGlobalCheatsFragment(game.titleId)
                                            )
                                            supportFragmentManager.executePendingTransactions()
                                            showFragmentScreen()
                                        },
                                    )
                                    item(
                                        key = "compress",
                                        title =
                                            if (game.isCompressed) {
                                                getString(R.string.decompress)
                                            } else {
                                                getString(R.string.compress)
                                            },
                                        enabled = !game.isInstalled,
                                        onClick = {
                                            val shouldCompress = !game.isCompressed
                                            val recommendedExt =
                                                NativeLibrary.getRecommendedExtension(
                                                    game.path,
                                                    shouldCompress,
                                                )
                                            val baseName = game.filename.substringBeforeLast('.')
                                            pendingCompressGame = game to shouldCompress
                                            compressDecompressLauncher.launch(
                                                "$baseName.$recommendedExt"
                                            )
                                        },
                                    )
                                    item(
                                        key = "open",
                                        title = getString(R.string.game_page_open_folders),
                                        onClick = { showOpenFoldersDialog(game) },
                                    )
                                    if (game.isInstalled) {
                                        item(
                                            key = "uninstall",
                                            title = getString(R.string.game_page_uninstall),
                                            onClick = { showUninstallDialog(game) },
                                        )
                                    }
                                    item(
                                        key = "cache",
                                        title = getString(R.string.delete_shader_cache),
                                        onClick = { showDeleteCacheDialog(game) },
                                    )
                                },
                        )
                    }
                }
            buildHomeRootPage(
                title = getString(R.string.app_name),
                settingsTitle = getString(R.string.preferences_settings),
                settingsSummary = getString(R.string.settings_description),
                optionsTitle = getString(R.string.home_options),
                searchTitle = getString(R.string.home_search),
                applicationsTitle = getString(R.string.home_games),
                gamesPage = gamesPage,
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
                onSettings = {
                    SettingsActivity.launch(this, SettingsFile.FILE_NAME_CONFIG, "")
                },
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

    // The game picked for compression in the home screen's game page, with whether it
    // should be compressed (true) or decompressed (false).
    private var pendingCompressGame: Pair<Game, Boolean>? = null
    private val compressDecompressLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
            uri: Uri? ->
                val pending = pendingCompressGame ?: return@registerForActivityResult
                pendingCompressGame = null
                if (uri != null) {
                    compressGameFile(pending.first, uri, pending.second)
                }
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
