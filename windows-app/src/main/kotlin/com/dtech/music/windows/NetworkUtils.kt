package com.dtech.music.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.UnknownHostException

object NetworkUtils {
    // For desktop, simply check if we can reach Google's DNS or another reliable host
    // to determine basic internet connectivity. A more complex check could involve
    // querying the OS for the active interface type.
    fun isWifiConnected(): Boolean {
        return true // Simplification for desktop
    }

    suspend fun isInternetAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName("8.8.8.8")
            address.isReachable(3000)
        } catch (e: Exception) {
            false
        }
    }
}
