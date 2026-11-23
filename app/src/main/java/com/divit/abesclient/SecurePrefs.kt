package com.divit.abesclient

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val FILE = "abes_credentials"
    @Volatile private var cached: SharedPreferences? = null

    /** Opening the encrypted store is slow (Keystore work), so open it once and reuse it. */
    private fun prefs(context: Context): SharedPreferences = cached ?: synchronized(this) {
        cached ?: open(context.applicationContext).also { cached = it }
    }

    private fun open(context: Context): SharedPreferences = try {
        create(context)
    } catch (e: Exception) {
        // The Keystore key can be lost (e.g. OS update, restored data). The old file can then never be
        // decrypted, so start fresh instead of crashing on every launch; the user re-enters credentials.
        Log.w("SecurePrefs", "Credential store unreadable, resetting: ${e.javaClass.simpleName}")
        context.deleteSharedPreferences(FILE)
        create(context)
    }

    private fun create(context: Context) = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun username(context: Context) = prefs(context).getString("username", "") ?: ""
    fun password(context: Context) = prefs(context).getString("password", "") ?: ""
    fun save(context: Context, username: String, password: String) {
        prefs(context).edit().putString("username", username).putString("password", password).apply()
    }
}
