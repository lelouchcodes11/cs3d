@file:JvmName("NetworkCompatKt")

package android.net

import android.net.http.SslCertificate

open class Network internal constructor(private val id: Int) {
    override fun toString(): String = id.toString()
    open fun getNetworkHandle(): Long = id.toLong()
}

open class NetworkCapabilities {
    companion object {
        const val NET_CAPABILITY_INTERNET = 12
        const val NET_CAPABILITY_VALIDATED = 16
        const val NET_CAPABILITY_NOT_METERED = 11
        const val NET_CAPABILITY_NOT_VPN = 15
        const val TRANSPORT_CELLULAR = 0
        const val TRANSPORT_WIFI = 1
        const val TRANSPORT_BLUETOOTH = 2
        const val TRANSPORT_ETHERNET = 3
        const val TRANSPORT_VPN = 4
    }

    open fun hasCapability(capability: Int): Boolean = true
    open fun hasTransport(transportType: Int): Boolean = transportType == TRANSPORT_ETHERNET || transportType == TRANSPORT_WIFI
    open fun getLinkDownstreamBandwidthKbps(): Int = 100_000
    open fun getLinkUpstreamBandwidthKbps(): Int = 100_000
}

open class NetworkRequest internal constructor() {
    class Builder {
        fun addCapability(capability: Int): Builder = this
        fun removeCapability(capability: Int): Builder = this
        fun addTransportType(transportType: Int): Builder = this
        fun removeTransportType(transportType: Int): Builder = this
        fun build(): NetworkRequest = NetworkRequest()
    }
}

@Deprecated("")
open class NetworkInfo {
    open fun isConnected(): Boolean = true
    open fun isConnectedOrConnecting(): Boolean = true
    open fun isAvailable(): Boolean = true
    open fun getType(): Int = ConnectivityManager.TYPE_WIFI
    open fun getTypeName(): String = "WIFI"
}

open class ConnectivityManager {
    companion object {
        const val TYPE_MOBILE = 0
        const val TYPE_WIFI = 1
        const val TYPE_ETHERNET = 9
        const val CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE"
    }

    open class NetworkCallback {
        open fun onAvailable(network: Network) {}
        open fun onLosing(network: Network, maxMsToLive: Int) {}
        open fun onLost(network: Network) {}
        open fun onUnavailable() {}
        open fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {}
        open fun onLinkPropertiesChanged(network: Network, linkProperties: Any?) {}
        open fun onBlockedStatusChanged(network: Network, blocked: Boolean) {}
    }

    private val defaultNetwork = Network(100)

    open fun registerNetworkCallback(request: NetworkRequest?, networkCallback: NetworkCallback) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { networkCallback.onAvailable(defaultNetwork) }
    }

    open fun registerDefaultNetworkCallback(networkCallback: NetworkCallback) = registerNetworkCallback(null, networkCallback)
    open fun unregisterNetworkCallback(networkCallback: NetworkCallback) {}
    open fun getActiveNetwork(): Network = defaultNetwork
    open fun getNetworkCapabilities(network: Network?): NetworkCapabilities = NetworkCapabilities()
    @Deprecated("")
    open fun getActiveNetworkInfo(): NetworkInfo = NetworkInfo()
    open fun isActiveNetworkMetered(): Boolean = false
    open fun getAllNetworks(): Array<Network> = arrayOf(defaultNetwork)
}

object TrafficStats {
    @JvmStatic
    fun getTotalRxBytes(): Long = -1

    @JvmStatic
    fun getUidRxBytes(uid: Int): Long = -1

    @JvmStatic
    fun setThreadStatsTag(tag: Int) {}
}

@Suppress("unused")
private val sslCertificateUse: Class<*> = SslCertificate::class.java
