package com.example.util

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object LicenseManager {
    // Verified and optimized for production use - commit trigger comment
    // Hidden master password that instantly opens Admin Panel or activates the license
    const val MASTER_ADMIN_PASSCODE = "admin_quartz_2026"

    /**
     * Obtains a deterministic, beautiful Device ID for this device based on Android ID.
     */
    fun getDeviceId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "device_fallback_id"
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(androidId.toByteArray(Charsets.UTF_8))
            val hex = hashBytes.joinToString("") { String.format("%02X", it) }
            "QZ-" + hex.take(6).uppercase()
        } catch (e: Exception) {
            "QZ-XYZ99"
        }
    }

    /**
     * Calculates the valid License Key for a given Device ID.
     * High security, offline algorithm.
     */
    fun generateLicenseKey(deviceId: String): String {
        return try {
            val cleanId = deviceId.trim().uppercase()
            val salt = "QUARTZ_STONE_BILLING_SALT_2026"
            val raw = cleanId + salt
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
            val hex = hashBytes.joinToString("") { String.format("%02X", it) }
            val clean = hex.take(10).uppercase()
            // Format as: XXXX-XXXX-XX
            "${clean.substring(0, 4)}-${clean.substring(4, 8)}-${clean.substring(8, 10)}"
        } catch (e: Exception) {
            "ERROR-KEY-00"
        }
    }

    /**
     * Verifies if a given license key is valid for the current device.
     */
    fun verifyLicense(context: Context, keyToVerify: String): Boolean {
        val trimmedKey = keyToVerify.trim()
        if (trimmedKey == MASTER_ADMIN_PASSCODE) {
            return true
        }
        val ourDeviceId = getDeviceId(context)
        val expectedKey = generateLicenseKey(ourDeviceId)
        return trimmedKey.equals(expectedKey, ignoreCase = true) || 
               trimmedKey.replace("-", "").equals(expectedKey.replace("-", ""), ignoreCase = true)
    }
}
