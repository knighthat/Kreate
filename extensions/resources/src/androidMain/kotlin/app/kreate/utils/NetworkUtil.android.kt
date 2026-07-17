package app.kreate.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.java.KoinJavaComponent.get


actual fun getNetworkMonitor(): StateFlow<Boolean> =
    callbackFlow {
        val manager = get<Context>(Context::class.java).getSystemService<ConnectivityManager>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable( network: Network ) {
                trySend( true )
            }

            override fun onLost( network: Network ) {
                trySend( false )
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager?.registerNetworkCallback( request, callback )
        awaitClose {
            manager?.unregisterNetworkCallback( callback )
        }
    // Use [SharingStarted.Eagerly] to actively notify listeners about changes
    }.stateIn(MainScope(), SharingStarted.Eagerly, false)