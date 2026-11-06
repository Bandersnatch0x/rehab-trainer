package com.rehabtrainer.app.core.network

import android.content.Context
import com.rehabtrainer.app.data.contract.TokenPair

/**
 * 持久化令牌存放：绑定一次，重启后仍保持登录。
 *
 * 说明：当前使用普通 SharedPreferences；生产环境应改为 EncryptedSharedPreferences
 * 或 Android Keystore 包裹（见 docs/delivery/next-actions.md P3-1）。
 */
class SharedPreferencesTokenStore(context: Context) : TokenStore {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun current(): TokenPair? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return TokenPair(
            accessToken = access,
            refreshToken = refresh,
            expiresIn = prefs.getInt(KEY_EXPIRES_IN, 0)
        )
    }

    override fun save(tokens: TokenPair) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putInt(KEY_EXPIRES_IN, tokens.expiresIn)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "rehab_device_session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_IN = "expires_in"
    }
}