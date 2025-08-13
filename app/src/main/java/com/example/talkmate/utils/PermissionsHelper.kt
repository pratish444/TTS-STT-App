package com.example.talkmate.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionsHelper(private val activity: ComponentActivity) {

    private var audioPermissionLauncher: ActivityResultLauncher<String>? = null
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    init {
        audioPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            onPermissionResult?.invoke(isGranted)
            onPermissionResult = null
        }
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestAudioPermission(onResult: ((Boolean) -> Unit)? = null) {
        onPermissionResult = onResult
        audioPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun shouldShowAudioPermissionRationale(): Boolean {
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        const val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO

        fun hasPermission(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}