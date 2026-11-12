package app.upcroc.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.upcroc.core.platform.NetworkMonitor

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
