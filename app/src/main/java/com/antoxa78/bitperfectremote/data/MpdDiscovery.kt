package com.antoxa78.bitperfectremote.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/** An MPD server found on the local network. */
data class DiscoveredMpdServer(
    // mDNS service name, or empty for servers found by the subnet scan.
    val name: String,
    val host: String,
    val port: Int,
    val viaMdns: Boolean
) {
    val key: String get() = "$host:$port"
}

/**
 * Finds MPD servers on the local network.
 *
 * MPD publishes itself through Zeroconf/Avahi as `_mpd._tcp` when its build
 * supports it, which is the fast path. As a fallback, the local /24 is probed
 * on MPD's default port and every answer is checked for the MPD greeting.
 */
class MpdDiscovery(context: Context) {
    private companion object {
        const val SERVICE_TYPE = "_mpd._tcp"
        const val DEFAULT_MPD_PORT = 6600
        const val MDNS_DISCOVERY_MS = 3000L
        const val SCAN_CONNECT_TIMEOUT_MS = 350
        const val SCAN_READ_TIMEOUT_MS = 500
        const val SCAN_BATCH_SIZE = 32
    }

    private val appContext = context.applicationContext
    private val nsdManager =
        appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    /**
     * Emits an updated, deduplicated list whenever a server is found. The flow
     * completes after the mDNS window and the subnet scan have both finished.
     */
    fun discover(): Flow<List<DiscoveredMpdServer>> = channelFlow {
        val found = ConcurrentHashMap<String, DiscoveredMpdServer>()

        fun record(server: DiscoveredMpdServer) {
            found.compute(server.key) { _, existing ->
                // A name from mDNS is more useful than the scan's bare address.
                if (existing == null || (server.viaMdns && !existing.viaMdns)) server else existing
            }
            trySend(sortedServers(found))
        }

        val mdnsJob = launch(Dispatchers.IO) {
            discoverViaMdns().collect { record(it) }
        }
        val scanJob = launch(Dispatchers.IO) {
            scanLocalNetwork(::record)
        }

        mdnsJob.join()
        scanJob.join()
        // Always emit once at the end so an empty result is observable.
        trySend(sortedServers(found))
    }

    private fun sortedServers(found: ConcurrentHashMap<String, DiscoveredMpdServer>) =
        found.values.sortedWith(
            compareByDescending<DiscoveredMpdServer> { it.viaMdns }
                .thenBy { it.name.ifBlank { it.host }.lowercase() }
                .thenBy { it.host }
        )

    @Suppress("DEPRECATION")
    private fun discoverViaMdns(): Flow<DiscoveredMpdServer> = callbackFlow {
        val nsd = nsdManager ?: run {
            close()
            return@callbackFlow
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                try {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolvedHost(resolved) ?: return
                            trySend(
                                DiscoveredMpdServer(
                                    name = resolved.serviceName.orEmpty(),
                                    host = host,
                                    port = resolved.port.takeIf { it in 1..65535 } ?: DEFAULT_MPD_PORT,
                                    viaMdns = true
                                )
                            )
                        }
                    })
                } catch (_: Exception) {
                    // Discovery is best-effort; the subnet scan is the fallback.
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) { close() }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
        }

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
            close()
            return@callbackFlow
        }

        val timeoutJob = launch {
            delay(MDNS_DISCOVERY_MS)
            close()
        }

        awaitClose {
            timeoutJob.cancel()
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvedHost(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val addresses = info.hostAddresses
            return (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())
                ?.hostAddress
        }
        return info.host?.hostAddress
    }

    private suspend fun scanLocalNetwork(onFound: (DiscoveredMpdServer) -> Unit) {
        val subnet = localSubnet() ?: return
        val hosts = (subnet.firstHost..subnet.lastHost).filter { it != subnet.localAddress }

        for (batch in hosts.chunked(SCAN_BATCH_SIZE)) {
            coroutineScope {
                batch.map { host ->
                    async(Dispatchers.IO) {
                        val address = ipv4ToString(host)
                        if (probeMpd(address)) {
                            onFound(
                                DiscoveredMpdServer(
                                    name = "",
                                    host = address,
                                    port = DEFAULT_MPD_PORT,
                                    viaMdns = false
                                )
                            )
                        }
                    }
                }.awaitAll()
            }
        }
    }

    // MPD sends "OK MPD <version>" as soon as a client connects. Checking the
    // greeting avoids listing unrelated services that happen to use port 6600.
    private fun probeMpd(host: String): Boolean {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host, DEFAULT_MPD_PORT), SCAN_CONNECT_TIMEOUT_MS)
            socket.soTimeout = SCAN_READ_TIMEOUT_MS
            socket.getInputStream().bufferedReader().readLine()?.startsWith("OK MPD") == true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    private data class LocalSubnet(
        val localAddress: Long,
        val firstHost: Long,
        val lastHost: Long
    )

    /**
     * Returns the IPv4 /24 around the device's Wi-Fi or Ethernet address. A
     * larger configured subnet is deliberately narrowed to /24: scanning more
     * than 254 hosts would take too long for an interactive "Scan" action.
     */
    @Suppress("DEPRECATION")
    private fun localSubnet(): LocalSubnet? {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null

        for (network in connectivityManager.allNetworks) {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                continue
            }

            val linkProperties = connectivityManager.getLinkProperties(network) ?: continue
            for (linkAddress in linkProperties.linkAddresses) {
                val address = linkAddress.address as? Inet4Address ?: continue
                if (address.isLoopbackAddress || address.isLinkLocalAddress) continue

                val ip = address.toIpLong()
                val prefixLength = linkAddress.prefixLength.coerceIn(24, 32)
                val hostBits = 32 - prefixLength
                val size = 1L shl hostBits
                val mask = (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
                val networkAddress = ip and mask
                val firstHost = networkAddress + 1
                val lastHost = networkAddress + size - 2
                if (lastHost >= firstHost) {
                    return LocalSubnet(ip, firstHost, lastHost)
                }
            }
        }
        return null
    }

    private fun Inet4Address.toIpLong(): Long {
        val bytes = address
        return ((bytes[0].toLong() and 0xFF) shl 24) or
            ((bytes[1].toLong() and 0xFF) shl 16) or
            ((bytes[2].toLong() and 0xFF) shl 8) or
            (bytes[3].toLong() and 0xFF)
    }

    private fun ipv4ToString(value: Long): String =
        "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}." +
            "${(value ushr 8) and 0xFF}.${value and 0xFF}"
}
