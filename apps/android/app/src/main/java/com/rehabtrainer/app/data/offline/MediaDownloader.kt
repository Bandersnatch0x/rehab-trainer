package com.rehabtrainer.app.data.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

class MediaDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** 下载器抽象：单测注入假实现，生产走 OkHttp 流式下载。 */
interface MediaDownloader {

    /**
     * 把 [url] 流式写入 [target]，返回字节数。
     *
     * 失败或超过 [maxBytes] 时抛 [MediaDownloadException]；调用方负责清理半成品。
     * 成功返回时文件已完整写入 [target]，但尚未改名，也不代表已登记索引。
     */
    suspend fun download(url: String, target: File, maxBytes: Long): Long
}

class HttpMediaDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaDownloader {

    override suspend fun download(url: String, target: File, maxBytes: Long): Long =
        withContext(ioDispatcher) {
            val request = Request.Builder().url(url).get().build()
            val call = client.newCall(request)
            // 协程取消时中断阻塞的 OkHttp 读，避免预取轮次取消后仍在后台写盘。
            val cancelHandle = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause is CancellationException) call.cancel()
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw MediaDownloadException("HTTP ${response.code}")
                    }
                    val body = response.body ?: throw MediaDownloadException("响应体为空")
                    val declaredLength = body.contentLength()
                    if (declaredLength > maxBytes) {
                        throw MediaDownloadException("文件超过缓存上限：$declaredLength")
                    }

                    target.parentFile?.mkdirs()
                    var total = 0L
                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > maxBytes) {
                                    throw MediaDownloadException("文件超过缓存上限：$total")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    if (total <= 0) throw MediaDownloadException("响应体为空")
                    total
                }
            } catch (e: IOException) {
                if (coroutineContext.job.isCancelled) {
                    throw CancellationException("下载已取消", e)
                }
                throw e
            } finally {
                cancelHandle.dispose()
            }
        }
}
