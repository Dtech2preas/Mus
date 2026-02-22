package com.example.musicdownloader

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

object NetworkUtils {
    const val USER_AGENT = "com.google.android.youtube/19.29.35 (Linux; U; Android 14; en_US) gzip"
}

object IPv4Dns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        val ipv4Addresses = addresses.filter { it is Inet4Address }

        if (ipv4Addresses.isNotEmpty()) {
            return ipv4Addresses
        }

        // If no IPv4 addresses are found, falling back to all addresses might be safer
        // than crashing, but the requirement is to FORCE IPv4.
        // However, usually if a host has IPv6 it also has IPv4 (Dual Stack).
        // If it's IPv6-only, we can't connect anyway with this constraint.
        // Returning the empty list or original list?
        // To be strict as requested:
        if (addresses.isEmpty()) {
             throw UnknownHostException("No addresses found for $hostname")
        }

        // If we only have IPv6, we return them (better than crashing? or throw?)
        // The user said "Force IPv4". If we return IPv6 here, we aren't forcing it.
        // But if the network/server is IPv6 only, failing is the correct behavior for "Force IPv4".
        // However, to be robust, if NO IPv4 is found, we might just throw or return empty.
        // Dns.SYSTEM throws UnknownHostException if nothing found.

        if (ipv4Addresses.isEmpty()) {
             // Throwing exception to strictly enforce IPv4 as requested,
             // assuming the delay issue is strictly related to IPv6 negotiation.
             throw UnknownHostException("No IPv4 addresses found for $hostname")
        }

        return ipv4Addresses
    }
}
