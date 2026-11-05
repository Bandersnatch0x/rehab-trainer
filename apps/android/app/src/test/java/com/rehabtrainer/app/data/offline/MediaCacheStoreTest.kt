package com.rehabtrainer.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaCacheStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun entry(url: String, bytes: Long = 10, pin: String? = null) = MediaCacheEntry(
        url = url,
        sha256 = sha256Hex(url),
        bytes = bytes,
        pinnedByPrescriptionId = pin,
        lastUsedAt = 1,
        cachedAt = 1,
        appVersion = "0.1.0"
    )

    private fun storeWithFiles(vararg entries: MediaCacheEntry): MediaCacheStore {
        val store = MediaCacheStore(folder.newFolder())
        entries.forEach { store.fileFor(it).writeBytes(ByteArray(it.bytes.toInt())) }
        return store
    }

    @Test
    fun 保存后完整读回() {
        val store = storeWithFiles()
        val entries = listOf(
            entry("https://media/a.mp4", pin = "p1"),
            entry("https://media/b.webm")
        )
        entries.forEach { store.fileFor(it).writeBytes(ByteArray(it.bytes.toInt())) }

        store.save(entries)

        assertEquals(entries, store.load())
    }

    @Test
    fun 索引损坏时返回空且不崩溃() {
        val store = MediaCacheStore(folder.newFolder())
        store.directory.mkdirs()
        File(store.directory, MediaCacheStore.INDEX_FILE).writeText("{ not json")

        assertTrue(store.load().isEmpty())
    }

    @Test
    fun 文件缺失的条目被丢弃() {
        val store = storeWithFiles()
        val kept = entry("https://media/kept.mp4")
        store.fileFor(kept).writeBytes(ByteArray(10))
        val missing = entry("https://media/missing.mp4")
        store.save(listOf(kept, missing))

        assertEquals(listOf(kept), store.load())
    }

    @Test
    fun 孤儿文件与半成品在加载时清理() {
        val store = storeWithFiles()
        val kept = entry("https://media/kept.mp4")
        store.fileFor(kept).writeBytes(ByteArray(10))
        store.save(listOf(kept))
        val orphan = File(store.directory, "orphan.bin").apply { writeBytes(ByteArray(3)) }
        val partial = File(store.directory, "abc.part").apply { writeBytes(ByteArray(3)) }

        store.load()

        assertFalse(orphan.exists())
        assertFalse(partial.exists())
        assertTrue(store.fileFor(kept).exists())
    }

    @Test
    fun 原子写不留下临时索引() {
        val store = storeWithFiles()
        store.save(listOf(entry("https://media/a.mp4")))

        assertTrue(File(store.directory, MediaCacheStore.INDEX_FILE).isFile)
        assertFalse(File(store.directory, "${MediaCacheStore.INDEX_FILE}.tmp").exists())
    }

    @Test
    fun 文件名使用摘要而非原始地址() {
        val cached = entry("https://media.example/dir/lesson-1.mp4?token=secret")

        assertEquals(64 + ".mp4".length, cached.fileName().length)
        assertFalse(cached.fileName().contains("media.example"))
        assertTrue(cached.fileName().endsWith(".mp4"))
    }
}
