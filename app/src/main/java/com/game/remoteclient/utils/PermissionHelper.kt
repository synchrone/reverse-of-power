package com.game.remoteclient.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object PermissionHelper {

    const val CAMERA_PERMISSION = Manifest.permission.CAMERA

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            CAMERA_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCameraPermission(
        fragment: Fragment,
        launcher: ActivityResultLauncher<String>
    ) {
        launcher.launch(CAMERA_PERMISSION)
    }
}
