package com.wmu.keban.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 本机凭据加密存储(Android Keystore + AES/GCM)
 *
 * 用途:保存用户在「我的」里配置的教务账号密码,仅供本机自动登录学校教务系统。
 * 安全约定:
 * - 密钥由 Android Keystore 生成并保管(不出安全硬件/系统密钥库),应用只持有别名
 * - 密文(AES/GCM,含随机 IV)存于应用私有 SharedPreferences
 * - **不写日志、不上传、不入 Git**;清除配置即删除密文
 */
object SecureStore {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "keban.jwxt.cred"
    private const val PREFS = "keban_secure"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun save(context: Context, kind: String, plain: String) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + cipherText
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(kind, Base64.encodeToString(combined, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context, kind: String): String? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(kind, null)
            ?: return null
        return runCatching {
            val combined = Base64.decode(stored, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LEN)
            val body = combined.copyOfRange(IV_LEN, combined.size)
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            }
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear(context: Context, kind: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(kind).apply()
    }

    fun has(context: Context, kind: String): Boolean = load(context, kind) != null

    const val KIND_ACCOUNT = "jwxt_account"
    const val KIND_PASSWORD = "jwxt_password"
}
