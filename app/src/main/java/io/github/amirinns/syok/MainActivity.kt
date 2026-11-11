package io.github.amirinns.syok

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.amirinns.syok.ui.LibraryScreen
import io.github.amirinns.syok.ui.NowPlayingScreen
import io.github.amirinns.syok.ui.PlayerViewModel
import io.github.amirinns.syok.ui.QueueScreen
import io.github.amirinns.syok.ui.theme.SyokNightBase
import io.github.amirinns.syok.ui.theme.SyokTheme

enum class SyokScreen {
    LIBRARY,
    NOW_PLAYING,
    QUEUE
}

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Head-unit full-screen window configuration (Milestone A2 Step 12)
        // Enable edge-to-edge drawing while respecting safe system insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        val requiredPermissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

        setContent {
            SyokTheme {
                var currentScreen by remember { mutableStateOf(SyokScreen.LIBRARY) }

                fun checkPermissions(): Boolean {
                    return requiredPermissions.all { perm ->
                        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
                    }
                }

                var hasPermission by remember { mutableStateOf(checkPermissions()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    val granted = grants.values.all { it }
                    hasPermission = granted
                    if (granted) {
                        playerViewModel.scanMedia()
                    }
                }

                LaunchedEffect(Unit) {
                    if (checkPermissions()) {
                        hasPermission = true
                        playerViewModel.scanMedia()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SyokNightBase)
                        .safeDrawingPadding() // Preserves essential controls away from system bars
                ) {
                    when (currentScreen) {
                        SyokScreen.LIBRARY -> {
                            LibraryScreen(
                                viewModel = playerViewModel,
                                hasPermission = hasPermission,
                                onGrantPermissionRequested = {
                                    permissionLauncher.launch(requiredPermissions)
                                },
                                onNavigateToNowPlaying = {
                                    currentScreen = SyokScreen.NOW_PLAYING
                                }
                            )
                        }
                        SyokScreen.NOW_PLAYING -> {
                            NowPlayingScreen(
                                viewModel = playerViewModel,
                                onNavigateToLibrary = {
                                    currentScreen = SyokScreen.LIBRARY
                                },
                                onNavigateToQueue = {
                                    currentScreen = SyokScreen.QUEUE
                                }
                            )
                        }
                        SyokScreen.QUEUE -> {
                            QueueScreen(
                                viewModel = playerViewModel,
                                onNavigateBack = {
                                    currentScreen = SyokScreen.NOW_PLAYING
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
