package com.rehabtrainer.app.data.offline

import com.rehabtrainer.app.data.contract.ApiError
import com.rehabtrainer.app.data.contract.SessionRecordCreate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 文件实现的待上传训练记录队列。
 *
 * - 以 `clientRecordId` 去重，重复入队返回 false；
 * - 写入采用「临时文件 + rename」，崩溃不会留下半截文件；
 * - 文件损坏时按空队列处理并保留原文件备份，不影响训练。
 */
class FileSessionUploadQueue(root: File) : SessionUploadQueue {

    private val file = File(root, FILE_NAME)
    private val backup = File(root, "$FILE_NAME.corrupt")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // 入队（训练页）与补传（启动/记录页）来自不同协程，整文件读改写必须串行。
    private val mutex = Mutex()

    override suspend fun enqueue(record: SessionRecordCreate): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val all = load().toMutableList()
            if (all.any { it.record.clientRecordId == record.clientRecordId }) {
                return@withContext false
            }
            all.add(StoredUpload(record = record, enqueuedAt = System.currentTimeMillis()))
            save(all)
            true
        }
    }

    override suspend fun pending(limit: Int): List<PendingSessionUpload> = mutex.withLock {
        withContext(Dispatchers.IO) {
            load().sortedBy { it.enqueuedAt }.take(limit).map { it.toPending() }
        }
    }

    override suspend fun markUploaded(clientRecordIds: Collection<String>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val ids = clientRecordIds.toSet()
            save(load().filterNot { it.record.clientRecordId in ids })
        }
    }

    override suspend fun markFailed(clientRecordIds: Collection<String>, error: ApiError) =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val ids = clientRecordIds.toSet()
                save(
                    load().map { stored ->
                        if (stored.record.clientRecordId in ids) {
                            stored.copy(
                                attemptCount = stored.attemptCount + 1,
                                lastAttemptAtEpochMillis = System.currentTimeMillis(),
                                lastErrorCode = error.code,
                                lastErrorMessage = error.message
                            )
                        } else {
                            stored
                        }
                    }
                )
            }
        }

    override suspend fun remove(clientRecordIds: Collection<String>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val ids = clientRecordIds.toSet()
            save(load().filterNot { it.record.clientRecordId in ids })
        }
    }

    override suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { save(emptyList()) }
    }

    override suspend fun pendingCount(): Int = mutex.withLock {
        withContext(Dispatchers.IO) { load().size }
    }

    private fun load(): List<StoredUpload> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<StoredQueue>(file.readText()).items
        }.getOrElse {
            runCatching { file.copyTo(backup, overwrite = true) }
            emptyList()
        }
    }

    private fun save(items: List<StoredUpload>) {
        // 先写临时文件，再原子替换。目标文件不先删除：rename 失败时必须保留旧队列。
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(json.encodeToString(StoredQueue(items)))
        if (!temp.renameTo(file)) {
            // Windows 上 rename 不能覆盖已存在的目标文件；只在这条兜底路径删旧文件。
            file.delete()
            check(temp.renameTo(file)) { "待上传队列原子替换失败：${file.absolutePath}" }
        }
    }

    @Serializable
    private data class StoredQueue(val items: List<StoredUpload> = emptyList())

    @Serializable
    private data class StoredUpload(
        val record: SessionRecordCreate,
        val enqueuedAt: Long,
        val attemptCount: Int = 0,
        val lastAttemptAtEpochMillis: Long? = null,
        val lastErrorCode: String? = null,
        val lastErrorMessage: String? = null
    ) {
        fun toPending(): PendingSessionUpload = PendingSessionUpload(
            record = record,
            enqueuedAtEpochMillis = enqueuedAt,
            attemptCount = attemptCount,
            lastAttemptAtEpochMillis = lastAttemptAtEpochMillis,
            lastError = lastErrorCode?.let { ApiError(it, lastErrorMessage ?: "") }
        )
    }

    private companion object {
        const val FILE_NAME = "session-uploads.json"
    }
}