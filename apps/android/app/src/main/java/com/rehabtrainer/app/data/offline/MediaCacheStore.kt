package com.rehabtrainer.app.data.offline

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * JSON 缓存索引的落盘层。
 *
 * 选型：一张索引表不值得引入 Room；JSON 足够小，且便于排障。
 * 原子写：先写 `index.json.tmp`，再重命名为 `index.json`，避免进程中断留下半份索引。
 * 清理：索引里缺失的条目与目录中的孤儿文件在 [load] 时一并丢弃；不按 `appVersion` 清理。
 */
class MediaCacheStore(
    val directory: File,
    private val json: Json = DEFAULT_JSON
) {

    private val indexFile: File = File(directory, INDEX_FILE)
    private val tempIndexFile: File = File(directory, "$INDEX_FILE.tmp")

    fun load(): List<MediaCacheEntry> {
        if (!directory.isDirectory) return emptyList()
        if (!indexFile.isFile) {
            // 索引不存在（首次安装或索引从未写成）：目录里的残留只能是孤儿/半成品。
            cleanupOrphans(emptyList())
            return emptyList()
        }
        // 索引存在但读取/解析失败：保留媒体文件，等下次成功写盘恢复，避免瞬时 IO 错误清空缓存。
        val text = runCatching { indexFile.readText(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        val parsed = runCatching {
            json.decodeFromString<List<MediaCacheEntry>>(text)
        }.getOrNull() ?: return emptyList()
        val valid = parsed.filter { entry ->
            entry.url.isNotBlank() &&
                isRemoteHttpUrl(entry.url) &&
                SHA256_PATTERN.matches(entry.sha256) &&
                entry.bytes > 0 &&
                fileFor(entry).isFile
        }
        cleanupOrphans(valid)
        return valid
    }

    fun save(entries: List<MediaCacheEntry>) {
        directory.mkdirs()
        val text = json.encodeToString(entries)
        tempIndexFile.writeText(text, Charsets.UTF_8)
        if (!tempIndexFile.renameTo(indexFile)) {
            // Windows 不允许 rename 覆盖目标文件；Android/Linux 上第一步即成功。
            indexFile.delete()
            check(tempIndexFile.renameTo(indexFile)) {
                "缓存索引原子替换失败：${indexFile.absolutePath}"
            }
        }
    }

    fun fileFor(entry: MediaCacheEntry): File = File(directory, entry.fileName())

    fun partialFileFor(sha256: String): File = File(directory, "$sha256.part")

    /** 删除索引未引用的文件（含上次中断留下的 `.part`）。 */
    fun cleanupOrphans(entries: List<MediaCacheEntry>) {
        val referenced = entries.mapTo(HashSet()) { fileFor(it).name }
        directory.listFiles()?.forEach { file ->
            if (file.name != INDEX_FILE && file.name != tempIndexFile.name && file.name !in referenced) {
                file.delete()
            }
        }
    }

    companion object {
        const val INDEX_FILE: String = "index.json"

        /** 只接受本层生成的 URL 摘要，防止被篡改的索引把删除路径带出缓存目录。 */
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

        val DEFAULT_JSON: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}
