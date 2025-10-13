package app.kreate.android.utils

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ConnectivityUtils: ConnectivityManager.NetworkCallback() {

    private val _isAvailable = MutableStateFlow(false)
    private val _isMetered = MutableStateFlow(false)

    /**
     * A [StateFlow] that emits current connectivity status.
     *
     * This state is immutable
     */
    val isAvailable = _isAvailable.asStateFlow()

    /**
     * A [StateFlow] that emits current connection metered
     *
     * This state is immutable
     */
    val isMetered = _isMetered.asStateFlow()

    override fun onAvailable( network: Network ) {
        super.onAvailable( network )

        _isAvailable.update { true }
    }

    override fun onUnavailable() {
        super.onUnavailable()

        _isAvailable.update { false }
    }

    override fun onCapabilitiesChanged( network: Network, networkCapabilities: NetworkCapabilities ) {
        super.onCapabilitiesChanged( network, networkCapabilities )

        _isMetered.update {
            !networkCapabilities.hasCapability( NetworkCapabilities.NET_CAPABILITY_NOT_METERED )
        }
    }
}