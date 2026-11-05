package com.rehabtrainer.app.data.offline

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** 真实流式下载的可复跑检查：本地回环 HTTP 服务，不依赖外网与额外测试依赖。 */
class HttpMediaDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var port: Int = 0

    @After
    fun stopServer() {
        runCatching { serverSocket?.close() }
        serverThread?.join(2_000)
    }

    @Test
    fun 流式下载完整写入目标文件() {
        startServer(status = "HTTP/1.1 200 OK", body = ByteArray(BODY_SIZE) { (it % 251).toByte() })
        val target = folder.newFile("video.part")

        val bytes = runBlocking {
            HttpMediaDownloader().download(url(), target, maxBytes = MAX_BYTES)
        }

        assertEquals(BODY_SIZE.toLong(), bytes)
        assertEquals(BODY_SIZE.toLong(), target.length())
        assertEquals(BODY_SIZE, target.readBytes().size)
    }

    @Test
    fun HTTP错误码抛出下载异常() {
        startServer(status = "HTTP/1.1 404 Not Found", body = ByteArray(0))
        val target = folder.newFile("missing.part")

        val error = assertThrows(MediaDownloadException::class.java) {
            runBlocking { HttpMediaDownloader().download(url(), target, maxBytes = MAX_BYTES) }
        }

        assertEquals("HTTP 404", error.message)
    }

    @Test
    fun 超过上限时中止下载() {
        startServer(status = "HTTP/1.1 200 OK", body = ByteArray(BODY_SIZE))
        val target = folder.newFile("oversized.part")

        assertThrows(MediaDownloadException::class.java) {
            runBlocking { HttpMediaDownloader().download(url(), target, maxBytes = 100L) }
        }
    }

    private fun url(): String = "http://127.0.0.1:$port/video.mp4"

    private fun startServer(status: String, body: ByteArray) {
        val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        port = socket.localPort
        serverThread = thread(isDaemon = true, name = "media-download-test-server") {
            try {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    var line = reader.readLine()
                    while (!line.isNullOrEmpty()) line = reader.readLine()
                    val header = buildString {
                        append(status).append("\r\n")
                        append("Content-Length: ").append(body.size).append("\r\n")
                        append("Content-Type: video/mp4\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    client.getOutputStream().apply {
                        write(header.toByteArray(Charsets.ISO_8859_1))
                        write(body)
                        flush()
                    }
                }
            } catch (_: IOException) {
                // 测试收尾时关闭 socket 会打断 accept，忽略即可。
            }
        }
    }

    private companion object {
        const val BODY_SIZE = 4096
        const val MAX_BYTES = 1_000_000L
    }
}
