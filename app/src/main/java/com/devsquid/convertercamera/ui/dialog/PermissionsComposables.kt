package com.devsquid.convertercamera.ui.dialog

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

enum class PermissionStatus { Granted, Denied, PermanentlyDenied, Unknown }

@Composable
fun rememberPermissionState(permission: String): Pair<PermissionStatus, () -> Unit> {
    val context = LocalContext.current
    var status by remember {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        mutableStateOf(if (granted) PermissionStatus.Granted else PermissionStatus.Unknown)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) {
            PermissionStatus.Granted
        } else {
            val canAskAgain = (context as? Activity)
                ?.shouldShowRequestPermissionRationale(permission) ?: false
            if (canAskAgain) PermissionStatus.Denied
            else PermissionStatus.PermanentlyDenied
        }
    }

    return status to { launcher.launch(permission) }
}


@Composable
fun PermissionRequestDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel ,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Grant") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
fun SettingsDialog(
    text: String,
    onCancel: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Permission Denied") },
        text = { Text("Enable camera access in Settings to continue.") },
        confirmButton = {
            TextButton(onClick = {
                // Send to app settings
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }) { Text("Open Settings") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}