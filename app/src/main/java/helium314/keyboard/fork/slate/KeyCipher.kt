// SPDX-License-Identifier: GPL-3.0-only
// Ported from SwiftSlate (github.com/Musheer360/SwiftSlate, manager/KeyCipher.kt), MIT License,
// Copyright (c) 2026 Musheer Alam. fork: package moved, unavailable below Android 6 (no keystore AES there).
package helium314.keyboard.fork.slate

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the API keys with an AES-256-GCM key kept in the Android keystore, so they are never stored in plain text. */
internal object KeyCipher {
    private const val KEY_ALIAS = "dt_slate_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_SEPARATOR = "]"

    @Volatile private var cachedSecretKey: SecretKey? = null

    val available: Boolean by lazy { init() }

    private fun init(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                keyGenerator.init(
                    KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .apply {
                            // keys are only needed while typing; below Android 15 the flag breaks key generation
                            // without a secure lock screen (SwiftSlate issue #141), so it is only set on 15+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) setUnlockedDeviceRequired(true)
                        }
                        .build()
                )
                keyGenerator.generateKey()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("KeyCipher", "Keystore init failed", e)
            false
        }
    }

    private fun secretKey(): SecretKey? {
        cachedSecretKey?.let { return it }
        if (!available) return null
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.also { cachedSecretKey = it }
        } catch (e: Exception) {
            null
        }
    }

    fun encrypt(plainText: String): String? {
        val key = secretKey() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val body = Base64.encodeToString(cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
            "$iv$IV_SEPARATOR$body"
        } catch (e: Exception) {
            null
        }
    }

    fun decrypt(encrypted: String): String? {
        val parts = encrypted.split(IV_SEPARATOR)
        if (parts.size != 2) return null
        return try {
            val key = secretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
