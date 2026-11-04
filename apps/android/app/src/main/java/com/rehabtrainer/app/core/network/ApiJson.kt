package com.rehabtrainer.app.core.network

import kotlinx.serialization.json.Json

/**
 * 全应用唯一的 JSON 配置。
 *
 * - `ignoreUnknownKeys`：服务端新增字段不导致旧客户端解析失败。
 * - `encodeDefaults = false`：请求体只发送调用方显式赋值的字段，匹配契约的可选字段语义。
 */
object ApiJson {

    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = false
    }
}
