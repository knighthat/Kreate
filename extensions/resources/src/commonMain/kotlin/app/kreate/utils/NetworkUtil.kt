package app.kreate.utils

import kotlinx.coroutines.flow.StateFlow


expect fun getNetworkMonitor(): StateFlow<Boolean>