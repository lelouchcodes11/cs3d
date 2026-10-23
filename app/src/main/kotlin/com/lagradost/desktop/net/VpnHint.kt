package com.lagradost.desktop.net

import java.net.NetworkInterface

/**
 * Some live sources (JIO TV, WILLOW and other DRMlive channels) refuse connections that come from a VPN or from Cloudflare WARP: the CDN answers 403 / 450 and the
 * wrapper site "Access denied", whatever the player sends, so a channel that played with WARP off plays nothing with it on. The player's error says so when such an
 * adapter is up, instead of only "loading failed".
 */
object VpnHint {
    private val NAMES = listOf("warp", "wintun", "wireguard", "vpn", "tap-", "nord", "proton", "openvpn", "tailscale", "zerotier")

    /** The name of the VPN-like adapter that is up (Cloudflare WARP, a VPN client ...), or null */
    fun adapter(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.map { it.displayName }
            .firstOrNull { n -> NAMES.any { n.contains(it, ignoreCase = true) } }
    }.getOrNull()

    /** The line to add to the error of a live source that did not play, or an empty text */
    fun noteForLive(): String = adapter()?.let {
        "\n\n\"$it\" is on. JIO TV, WILLOW and other live sources refuse VPN and Cloudflare WARP connections (they answer 403 or 450 to any player): turn it off, or exclude CloudStream in its split tunnel settings, and try again."
    } ?: ""
}
