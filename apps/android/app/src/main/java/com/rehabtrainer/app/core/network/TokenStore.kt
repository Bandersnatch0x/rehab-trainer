package com.rehabtrainer.app.core.network

import com.rehabtrainer.app.data.contract.TokenPair

/**
 * 令牌存放抽象。
 *
 * 默认实现只在内存中保存；接入设备安全存储（如 EncryptedSharedPreferences）时替换本接口即可，
 * 网络层与 UI 层不需要改动。
 */
interface TokenStore {

    fun current(): TokenPair?

    fun save(tokens: TokenPair)

    fun clear()
}

class InMemoryTokenStore(initial: TokenPair? = null) : TokenStore {

    @Volatile
    private var tokens: TokenPair? = initial

    override fun current(): TokenPair? = tokens

    override fun save(tokens: TokenPair) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }
}
