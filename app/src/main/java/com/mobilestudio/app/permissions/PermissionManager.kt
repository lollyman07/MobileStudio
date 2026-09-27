package com.mobilestudio.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Central place to check the exact permissions each feature needs (Section 15 — request only what's necessary). */
object PermissionManager {

    fun hasCamera(context: Context) = granted(context, Manifest.permission.CAMERA)
    fun hasMicrophone(context: Context) = granted(context, Manifest.permission.RECORD_AUDIO)

    fun hasNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            granted(context, Manifest.permission.POST_NOTIFICATIONS) else true

    fun hasMediaImages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            granted(context, Manifest.permission.READ_MEDIA_IMAGES) else true

    fun hasMediaVideo(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            granted(context, Manifest.permission.READ_MEDIA_VIDEO) else true

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun requiredRuntimePermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
            list += Manifest.permission.READ_MEDIA_IMAGES
            list += Manifest.permission.READ_MEDIA_VIDEO
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return list.toTypedArray()
    }
}
