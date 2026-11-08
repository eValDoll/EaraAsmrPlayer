package com.asmr.player.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class PlaybackNetworkObserver(
    context: Context,
    private val scope: CoroutineScope,
    private val onNetworkChanged: () -> Unit,
) : AutoCloseable {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val initialNetwork = connectivity.activeNetwork
    private val initialCapabilities = initialNetwork?.let(connectivity::getNetworkCapabilities)
    private val networkState = PlaybackNetworkState(
        initialNetwork?.networkHandle,
        initialCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
    )
    @Suppress("DEPRECATION")
    private val initialUnderlyingNetworks =
        connectivity.allNetworks.mapNotNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            ) network.networkHandle to capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            else null
        }.toMap()
    private val underlyingNetworks = PlaybackUnderlyingNetworks(initialUnderlyingNetworks)
    private val bestNetworkState = PlaybackNetworkState(
        initialNetwork?.networkHandle.takeIf {
            initialCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true
        },
        initialCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true &&
            initialCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        ignoreInitialCapabilities = initialCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true &&
            initialUnderlyingNetworks.isNotEmpty(),
    )
    private var closed = false
    private val registeredCallbacks = mutableListOf<ConnectivityManager.NetworkCallback>()
    private var networkJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = dispatch {
            networkState.available(network.networkHandle)
        }

        override fun onLost(network: Network) = dispatch {
            networkState.lost(network.networkHandle)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = dispatch {
            if (networkState.validated(
                    network.networkHandle,
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            ) scheduleNetworkRecovery()
        }
    }

    private val underlyingCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = dispatch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bestNetworkState.available(network.networkHandle)
            } else {
                underlyingNetworks.available(network.networkHandle)
            }
        }

        override fun onLost(network: Network) = dispatch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bestNetworkState.lost(network.networkHandle)
            } else if (underlyingNetworks.lost(network.networkHandle)) {
                scheduleNetworkRecovery()
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = dispatch {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val changed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bestNetworkState.validated(network.networkHandle, validated)
            } else {
                underlyingNetworks.validated(network.networkHandle, validated)
            }
            if (changed) scheduleNetworkRecovery()
        }
    }

    init {
        runCatching {
            connectivity.registerDefaultNetworkCallback(callback)
            registeredCallbacks.add(callback)
        }
            .onFailure { Log.w("PlaybackNetworkObserver", "Unable to observe playback network", it) }
        // VPN 的 Network 对象可能不变；仅跟随最优物理链路，避免备用移动网络验证后打断 Wi-Fi 音频流。
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                connectivity.registerBestMatchingNetworkCallback(
                    request, underlyingCallback, Handler(Looper.getMainLooper()),
                )
            } else {
                connectivity.registerNetworkCallback(request, underlyingCallback)
            }
            registeredCallbacks.add(underlyingCallback)
        }.onFailure { Log.w("PlaybackNetworkObserver", "Unable to observe underlying playback networks", it) }
    }

    private fun dispatch(action: () -> Unit) {
        scope.launch { if (!closed) action() }
    }

    private fun scheduleNetworkRecovery() {
        networkJob?.cancel()
        networkJob = scope.launch {
            // 合并默认网络和物理链路的通知，避免同一次恢复反复取消音频请求。
            delay(500L)
            if (!closed) onNetworkChanged()
        }
    }

    override fun close() {
        closed = true
        networkJob?.cancel()
        registeredCallbacks.forEach { runCatching { connectivity.unregisterNetworkCallback(it) } }
        registeredCallbacks.clear()
    }
}

internal class PlaybackUnderlyingNetworks(initialNetworks: Map<Long, Boolean>) {
    private val networks = initialNetworks.toMutableMap()

    fun available(handle: Long) {
        if (networks.containsKey(handle)) return
        networks[handle] = false
    }

    fun lost(handle: Long): Boolean = networks.remove(handle) != null && networks.isNotEmpty()

    fun validated(handle: Long, validated: Boolean): Boolean {
        val previous = networks[handle] ?: return false
        networks[handle] = validated
        return validated && !previous
    }
}

internal class PlaybackNetworkState(
    private var network: Long?,
    private var isValidated: Boolean,
    private var ignoreInitialCapabilities: Boolean = false,
) {
    fun available(handle: Long) {
        if (handle == network) return
        network = handle
        isValidated = false
    }

    fun lost(handle: Long) {
        if (handle == network) {
            network = null
            isValidated = false
            ignoreInitialCapabilities = false
        }
    }

    fun validated(handle: Long, validated: Boolean): Boolean {
        if (handle != network) return false
        val restored = validated && !isValidated
        isValidated = validated
        if (ignoreInitialCapabilities) {
            ignoreInitialCapabilities = false
            return false
        }
        return restored
    }
}
