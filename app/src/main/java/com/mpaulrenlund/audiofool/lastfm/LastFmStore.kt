package com.mpaulrenlund.audiofool.lastfm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Last.fm API key, secret and session (spec 7), stored encrypted: AES-GCM
 * with a key that lives in the Android Keystore and never leaves it. Each value
 * is saved as the IV and the ciphertext, in Base64, in its own SharedPreferences
 * file. The desktop keeps them in its settings file (protected by Windows' DPAPI).
 *
 * A value that can't be decrypted (the Keystore key gone, as after a restore onto
 * another phone) reads as missing, so the app asks to connect again.
 */
class LastFmStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    class Saved(val apiKey: String, val secret: String, val userName: String?, val sessionKey: String?)

    fun read(): Saved? {
        val apiKey = get(KEY_API_KEY) ?: return null
        val secret = get(KEY_SECRET) ?: return null
        return Saved(apiKey, secret, get(KEY_USER), get(KEY_SESSION))
    }

    /** The key and secret, once Last.fm has shown they work (a token came back). */
    fun saveKeys(apiKey: String, secret: String) {
        prefs.edit().putString(KEY_API_KEY, encrypt(apiKey)).putString(KEY_SECRET, encrypt(secret)).apply()
    }

    fun saveSession(userName: String, sessionKey: String) {
        prefs.edit().putString(KEY_USER, encrypt(userName)).putString(KEY_SESSION, encrypt(sessionKey)).apply()
    }

    /** Disconnect: the session goes, the key and secret stay for next time (as the desktop's). */
    fun forgetSession() {
        prefs.edit().remove(KEY_USER).remove(KEY_SESSION).apply()
    }

    private fun get(name: String): String? = prefs.getString(name, null)?.let(::decrypt)

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = try {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).toString(Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val FILE = "lastfm"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "audiofool.lastfm"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val KEY_API_KEY = "apiKey"
        private const val KEY_SECRET = "secret"
        private const val KEY_USER = "userName"
        private const val KEY_SESSION = "session"
    }
}
