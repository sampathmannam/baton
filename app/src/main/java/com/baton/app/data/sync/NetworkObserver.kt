package com.baton.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v1.4 (PHONE-FINDING-6): thin wrapper around
 * [ConnectivityManager.NetworkCallback]. Exposes a single
 * [isOnline] [StateFlow] that the UI can collect with
 * `collectAsStateWithLifecycle()` to render an offline pill.
 *
 * Lifecycle. The callback is registered when [start] is called
 * and unregistered when [stop] is called. MainActivity calls
 * `start()` in `onStart()` and `stop()` in `onStop()` so the
 * observer is active while the activity is in the foreground.
 *
 * Default value. [isOnline] defaults to `true` so the UI never
 * flashes an "Offline" pill for the brief moment before the
 * first callback arrives.
 *
 * Scope. Singleton so multiple activities or composables share
 * the same `isOnline` flow.
 */
@Singleton
class NetworkObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager: ConnectivityManager? =
        context.getSystemService()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var registeredCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (registeredCallback != null) return
        val cm = connectivityManager ?: return
        val initial = cm.activeNetwork
            ?.let { cm.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _isOnline.value = initial
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }
            override fun onLost(network: Network) {
                val cm = connectivityManager ?: return
                val anyOnline = cm.activeNetwork
                    ?.let { cm.getNetworkCapabilities(it) }
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                _isOnline.value = anyOnline
            }
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                _isOnline.value =
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        cm.registerNetworkCallback(request, callback)
        registeredCallback = callback
    }

    fun stop() {
        val callback = registeredCallback ?: return
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
        registeredCallback = null
    }
}
