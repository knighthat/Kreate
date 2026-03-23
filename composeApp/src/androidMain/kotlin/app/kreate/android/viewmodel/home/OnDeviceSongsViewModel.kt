package app.kreate.android.viewmodel.home

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import it.fast4x.rimusic.utils.isAtLeastAndroid13


class OnDeviceSongsViewModel(context: Context) : ViewModel() {

    companion object {
        val PERMISSION = if( isAtLeastAndroid13 ) READ_MEDIA_AUDIO else READ_EXTERNAL_STORAGE
    }

    var isPermissionGranted by mutableStateOf( isPermissionGranted(context) )

    fun onPermissionGranted( result: Boolean ) { isPermissionGranted = result }

    fun isPermissionGranted( context: Context ): Boolean =
        ContextCompat.checkSelfPermission( context, PERMISSION ) == PackageManager.PERMISSION_GRANTED
}