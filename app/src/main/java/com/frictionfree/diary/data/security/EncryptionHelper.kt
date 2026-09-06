package com.frictionfree.diary.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object EncryptionHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "FrictionFreeDiaryKey"
    private const val PREFS_FILE = "friction_free_secure_prefs"
    private const val PREF_IS_ENCRYPTED = "pref_is_encrypted"
    private const val PREF_PIN_HASH = "pref_pin_hash"
    private const val PREF_PIN_SALT = "pref_pin_salt"
    private const val PREF_DB_PASSPHRASE = "pref_db_passphrase"

    fun getSecurePrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback for devices without hardware keystore support in development
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun isEncryptionEnabled(context: Context): Boolean {
        return getSecurePrefs(context).getBoolean(PREF_IS_ENCRYPTED, false)
    }

    fun setEncryptionEnabled(context: Context, enabled: Boolean) {
        getSecurePrefs(context).edit().putBoolean(PREF_IS_ENCRYPTED, enabled).apply()
    }

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val saltHex = bytesToHex(salt)
        val hash = hashWithSalt(pin, saltHex)

        // Generate a random 256-bit passphrase for SQLCipher if not existing
        val prefs = getSecurePrefs(context)
        if (!prefs.contains(PREF_DB_PASSPHRASE)) {
            val dbKey = ByteArray(32)
            SecureRandom().nextBytes(dbKey)
            prefs.edit().putString(PREF_DB_PASSPHRASE, bytesToHex(dbKey)).apply()
        }

        prefs.edit()
            .putString(PREF_PIN_HASH, hash)
            .putString(PREF_PIN_SALT, saltHex)
            .putBoolean(PREF_IS_ENCRYPTED, true)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = getSecurePrefs(context)
        val storedHash = prefs.getString(PREF_PIN_HASH, null) ?: return true
        val saltHex = prefs.getString(PREF_PIN_SALT, null) ?: return true
        val computedHash = hashWithSalt(pin, saltHex)
        return storedHash == computedHash
    }

    fun hasPinSet(context: Context): Boolean {
        return getSecurePrefs(context).getString(PREF_PIN_HASH, null) != null
    }

    fun getDatabasePassphrase(context: Context): ByteArray? {
        if (!isEncryptionEnabled(context)) return null
        val prefs = getSecurePrefs(context)
        val hex = prefs.getString(PREF_DB_PASSPHRASE, null) ?: return null
        return hexToBytes(hex)
    }

    private fun hashWithSalt(input: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(hexToBytes(saltHex))
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(hash)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
