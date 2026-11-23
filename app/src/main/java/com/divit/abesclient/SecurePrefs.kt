package com.divit.abesclient

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        "abes_credentials",
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
