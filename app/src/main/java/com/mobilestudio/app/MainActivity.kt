package com.mobilestudio.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobilestudio.app.permissions.PermissionManager
import com.mobilestudio.app.ui.navigation.MobileStudioNavHost
import com.mobilestudio.app.ui.studio.StudioViewModel
import com.mobilestudio.app.ui.theme.MobileStudioTheme

/**
 * Single-activity host (Section 17). Runtime permissions (Section 15) are requested up front,
 * one dialog, only for what the app actually needs — camera, mic, notifications, media read on
 * API 33+. Screen-capture and other one-off system permissions are requested contextually from
 * the screen that needs them (see StudioScreen's MediaProjection launcher).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* results observed via PermissionManager on demand */ }
            LaunchedEffect(Unit) {
                permissionLauncher.launch(PermissionManager.requiredRuntimePermissions())
            }

            val viewModel: StudioViewModel = viewModel()
            val state by viewModel.uiState.collectAsState()

            MobileStudioTheme(themeMode = state.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MobileStudioNavHost()
                }
            }
        }
    }
}
