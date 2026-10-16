package com.pfpt.localvideoplayer.ui.components

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.cast.CastManager
import com.pfpt.localvideoplayer.cast.CastUiState
import com.pfpt.localvideoplayer.util.Permissions

data class CastSheetActions(
    val showSheet: Boolean,
    val open: () -> Unit,
    val dismiss: (CastUiState) -> Unit,
)

@Composable
fun rememberCastSheetActions(
    castManager: CastManager?,
    localPlayer: Player? = null,
): CastSheetActions {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.isNotEmpty() && results.values.all { it }
        if (granted) {
            castManager?.bindLocalPlayer(localPlayer)
            castManager?.startDiscovery()
            showSheet = true
        } else {
            Toast.makeText(context, R.string.cast_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    fun open() {
        val manager = castManager ?: return
        val permissions = Permissions.castDiscoveryPermissions
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        manager.bindLocalPlayer(localPlayer)
        manager.startDiscovery()
        showSheet = true
    }

    fun dismiss(castState: CastUiState) {
        showSheet = false
        if (castState !is CastUiState.Connected && castState !is CastUiState.Connecting) {
            castManager?.stopDiscovery()
        }
    }

    return CastSheetActions(
        showSheet = showSheet,
        open = ::open,
        dismiss = ::dismiss,
    )
}
