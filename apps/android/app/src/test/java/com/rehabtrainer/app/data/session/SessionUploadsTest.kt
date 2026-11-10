package com.rehabtrainer.app.data.session

import com.rehabtrainer.app.core.network.ApiResult
import com.rehabtrainer.app.data.contract.ApiError
import com.rehabtrainer.app.data.contract.SessionBatchItemResult
import com.rehabtrainer.app.data.contract.SessionBatchRequest
import com.rehabtrainer.app.data.contract.SessionBatchResult
import com.rehabtrainer.app.data.contract.SessionRecordCreate
import com.rehabtrainer.app.data.offline.ApiSessionSyncService
import com.rehabtrainer.app.data.offline.FileSessionUploadQueue
import com.rehabtrainer.app.data.offline.PendingSessionUpload
import com.rehabtrainer.app.data.offline.SessionSyncService
import com.rehabtrainer.app.data.offline.SessionUploadQueue
import com.rehabtrainer.app.data.offline.UploadBackoffPolicy
import com.rehabtrainer.app.data.offline.UploadStatus
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionRecordBuilderTest {

    @Test
    fun buildsRecordWithVersionsAndMetrics() {
        val record = SessionRecordBuilder.build(
            patientId = "patient-1",
            prescriptionId = "rx-1",
            outcome = SetOutcome(
                exerciseCode = "ex-gait-01",
                setIndex = 2,
                totalSets = 3,
                completionCount = 12,
                movementErrorCount = 1,
                interruptionCount = 0,
                validFrameRatio = 0.9,
                recordedAtIso = "2026-11-07T02:00:00Z",
                clientRecordId = "client-1"
            ),
            modelSha256 = "abc"
        )

        assertEquals("client-1", record.clientRecordId)
        assertEquals("patient-1", record.patientId)
        assertEquals("rx-1", record.prescriptionId)
        assertEquals(12, record.completionCount)
        assertEquals(1, record.movementErrorCount)
        assertEquals(0.9, record.validFrameRatio!!, 1e-9)
        assertNotNull(record.metrics)
        assertEquals("ex-gait-01", record.metrics!!["exerciseCode"].toString().trim('"'))
    }

    @Test
    fun validFrameRatioHandlesMissingFrames() {
        assertNull(SessionRecordBuilder.validFrameRatio(validFrames = 0, totalFrames = 0))
        assertEquals(0.5, SessionRecordBuilder.validFrameRatio(5, 10)!!, 1e-9)
    }
}

class FileSessionUploadQueueTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun record(id: String) = SessionRecordCreate(
        clientRecordId = id,
        patientId = "patient-1",
        prescriptionId = "rx-1",
        recordedAt = "2026-11-07T02:00:00Z",
        modelVersion = "m",
        modelSha256 = "a".repeat(64),
        metricAlgorithmVersion = "alg",
        thresholdConfigVersion = "th"
    )

    @Test
    fun enqueueDeduplicatesAndPersistsAcrossInstances() = runBlocking {
        val root = folder.newFolder("queue")
        val queue = FileSessionUploadQueue(root)

        assertTrue(queue.enqueue(record("client-1"), null))
        assertFalse(queue.enqueue(record("client-1"), null))
        assertEquals(1, queue.pendingCount())

        // 新实例读取同一文件，模拟进程重启。
        val reopened = FileSessionUploadQueue(root)
        assertEquals(1, reopened.pendingCount())
        assertEquals("client-1", reopened.pending().first().clientRecordId)
    }

    @Test
    fun markUploadedRemovesAndMarkFailedKeeps() = runBlocking {
        val queue = FileSessionUploadQueue(folder.newFolder("queue2"))
        queue.enqueue(record("client-1"), null)
        queue.enqueue(record("client-2"), null)

        queue.markUploaded(listOf("client-1"))
        assertEquals(1, queue.pendingCount())

        queue.markFailed(listOf("client-2"), ApiError("INTERNAL_ERROR", "boom"))
        val pending = queue.pending().first()
        assertEquals(1, pending.attemptCount)
        assertEquals("INTERNAL_ERROR", pending.lastError?.code)
    }

    @Test
    fun corruptFileYieldsEmptyQueue() = runBlocking {
        val root = folder.newFolder("queue3")
        java.io.File(root, "session-uploads.json").writeText("not-json")
        assertEquals(0, FileSessionUploadQueue(root).pendingCount())
    }

    @Test
    fun ownerScopedPendingAndQuarantinePersist() = runBlocking {
        val root = folder.newFolder("queue4")
        val queue = FileSessionUploadQueue(root)
        queue.enqueue(record("mine"), "acc-1")
        queue.enqueue(record("other"), "acc-2")

        assertEquals(
            listOf("mine"),
            queue.pending(ownerAccountId = "acc-1").map { it.clientRecordId }
        )
        queue.quarantine(listOf("mine"), ApiError("VALIDATION_FAILED", "bad"))

        assertTrue(queue.pending(ownerAccountId = "acc-1").isEmpty())
        assertEquals(1, queue.heldCount("acc-1"))
        assertEquals(2, queue.pendingCount())

        // 重新打开：归属、隔离状态与错误都落盘。
        val reopened = FileSessionUploadQueue(root)
        assertEquals(1, reopened.heldCount("acc-1"))
        assertEquals(0, reopened.pending(ownerAccountId = "acc-1").size)
    }

    @Test
    fun legacyEntryWithoutOwnerIsNotUploadedOrAdopted() = runBlocking {
        val queue = FileSessionUploadQueue(folder.newFolder("queue5"))
        queue.enqueue(record("legacy"), null)
        queue.markFailed(listOf("legacy"), ApiError("INTERNAL_ERROR", "boom"))

        // 升级前的条目没有归属：不能被当前账号上传，也不能被重试入口认领。
        assertTrue(queue.pending(ownerAccountId = "acc-1").isEmpty())
        assertEquals(0, queue.heldCount("acc-1"))
        queue.retryHeld("acc-1")
        assertTrue(queue.pending(ownerAccountId = "acc-1").isEmpty())
        assertEquals(1, queue.pendingCount())

        // 登出清理会按「归属未知」删掉它。
        queue.clearByOwner(null)
        assertEquals(0, queue.pendingCount())
    }

    @Test
    fun clearByOwnerLeavesOtherAccountsIntact() = runBlocking {
        val queue = FileSessionUploadQueue(folder.newFolder("queue6"))
        queue.enqueue(record("a"), "acc-1")
        queue.enqueue(record("b"), "acc-2")

        queue.clearByOwner("acc-1")

        assertTrue(queue.pending(ownerAccountId = "acc-1").isEmpty())
        assertEquals(
            listOf("b"),
            queue.pending(ownerAccountId = "acc-2").map { it.clientRecordId }
        )
        assertEquals(1, queue.pendingCount())
    }
}

class ApiSessionSyncServiceTest {

    private class InMemoryQueue : SessionUploadQueue {
        val items = linkedMapOf<String, PendingSessionUpload>()

        override suspend fun enqueue(
            record: SessionRecordCreate,
            ownerAccountId: String?
        ): Boolean = items.putIfAbsent(
            record.clientRecordId,
            PendingSessionUpload(
                record = record,
                enqueuedAtEpochMillis = 0L,
                ownerAccountId = ownerAccountId
            )
        ) == null

        override suspend fun pending(
            limit: Int,
            ownerAccountId: String?
        ): List<PendingSessionUpload> = items.values
            .filter { it.status == UploadStatus.PENDING }
            .filter { ownerAccountId == null || it.ownerAccountId == ownerAccountId }
            .take(limit)

        override suspend fun markUploaded(clientRecordIds: Collection<String>) {
            clientRecordIds.forEach { items.remove(it) }
        }

        override suspend fun markFailed(clientRecordIds: Collection<String>, error: ApiError) {
            clientRecordIds.forEach { id ->
                items[id]?.let {
                    items[id] = it.copy(
                        attemptCount = it.attemptCount + 1,
                        lastError = error
                    )
                }
            }
        }

        override suspend fun quarantine(clientRecordIds: Collection<String>, error: ApiError) {
            clientRecordIds.forEach { id ->
                items[id]?.let {
                    items[id] = it.copy(
                        attemptCount = it.attemptCount + 1,
                        lastError = error,
                        status = UploadStatus.QUARANTINED
                    )
                }
            }
        }

        override suspend fun remove(clientRecordIds: Collection<String>) {
            clientRecordIds.forEach { items.remove(it) }
        }

        override suspend fun clear() {
            items.clear()
        }

        override suspend fun clearByOwner(ownerAccountId: String?) {
            items.entries.removeAll { it.value.ownerAccountId == ownerAccountId }
        }

        override suspend fun heldCount(ownerAccountId: String): Int = items.values.count {
            it.status == UploadStatus.QUARANTINED && it.ownerAccountId == ownerAccountId
        }

        override suspend fun retryHeld(ownerAccountId: String) {
            items.keys.toList().forEach { id ->
                val entry = items[id] ?: return@forEach
                if (
                    entry.status == UploadStatus.QUARANTINED &&
                    entry.ownerAccountId == ownerAccountId
                ) {
                    items[id] = entry.copy(
                        status = UploadStatus.PENDING,
                        attemptCount = 0,
                        lastAttemptAtEpochMillis = null,
                        lastError = null
                    )
                }
            }
        }

        override suspend fun pendingCount(): Int = items.size
    }

    private companion object {
        const val OWNER = "acc-1"
    }

    private fun record(id: String) = SessionRecordCreate(
        clientRecordId = id,
        patientId = "patient-1",
        prescriptionId = "rx-1",
        recordedAt = "2026-11-07T02:00:00Z",
        modelVersion = "m",
        modelSha256 = "a".repeat(64),
        metricAlgorithmVersion = "alg",
        thresholdConfigVersion = "th"
    )

    @Test
    fun acceptedRecordsLeaveQueue() = runBlocking {
        val queue = InMemoryQueue()
        queue.enqueue(record("client-1"), OWNER)
        val service = ApiSessionSyncService(queue) { request ->
            SessionBatchResult(request.records.map { SessionBatchItemResult(it.clientRecordId, true) })
        }

        val result = service.flushPending(ownerAccountId = OWNER)
        assertTrue(result is ApiResult.Success)
        assertEquals(0, queue.pendingCount())
    }

    @Test
    fun retryableRejectionStaysAndNonRetryableLeaves() = runBlocking {
        val queue = InMemoryQueue()
        queue.enqueue(record("retry"), OWNER)
        queue.enqueue(record("reject"), OWNER)
        val service = ApiSessionSyncService(queue) { request ->
            SessionBatchResult(
                request.records.map { item ->
                    when (item.clientRecordId) {
                        "retry" -> SessionBatchItemResult(
                            item.clientRecordId,
                            accepted = false,
                            error = ApiError("INTERNAL_ERROR", "busy")
                        )
                        else -> SessionBatchItemResult(
                            item.clientRecordId,
                            accepted = false,
                            error = ApiError("VALIDATION_FAILED", "bad")
                        )
                    }
                }
            )
        }

        service.flushPending(ownerAccountId = OWNER)

        assertEquals(
            listOf("retry"),
            queue.pending(ownerAccountId = OWNER).map { it.clientRecordId }
        )
        assertEquals(UploadStatus.QUARANTINED, queue.items["reject"]!!.status)
        assertEquals("VALIDATION_FAILED", queue.items["reject"]!!.lastError?.code)
        assertEquals(1, queue.items["retry"]!!.attemptCount)
    }

    @Test
    fun uploadFailureKeepsQueue() = runBlocking {
        val queue = InMemoryQueue()
        queue.enqueue(record("client-1"), OWNER)
        val service = ApiSessionSyncService(queue) { throw IOException("offline") }

        val result = service.flushPending(ownerAccountId = OWNER)

        assertTrue(result is ApiResult.Failure)
        assertEquals(1, queue.pendingCount())
    }

    @Test
    fun itemsInBackoffAreSkippedWithoutNetworkCall() = runBlocking {
        val queue = InMemoryQueue()
        // 已失败 3 次 → 退避 120s；现在只过去 10s，本轮不该发请求。
        queue.items["client-1"] = PendingSessionUpload(
            record = record("client-1"),
            enqueuedAtEpochMillis = 0L,
            attemptCount = 3,
            lastAttemptAtEpochMillis = 1_000L,
            ownerAccountId = OWNER
        )
        var calls = 0
        val service = ApiSessionSyncService(queue, nowMillis = { 11_000L }) {
            calls++
            SessionBatchResult(emptyList())
        }

        val result = service.flushPending(ownerAccountId = OWNER)

        assertEquals(0, calls)
        // 退避中返回空成功而不是失败：没有错误可提示，只是本轮没传。
        assertTrue(result is ApiResult.Success)
        assertEquals(1, queue.pendingCount())
    }

    @Test
    fun dueItemIsUploadedEvenWhenAnotherIsInBackoff() = runBlocking {
        val queue = InMemoryQueue()
        // 5 次失败 → 退避 480s，仍在等。
        queue.items["blocked"] = PendingSessionUpload(
            record = record("blocked"),
            enqueuedAtEpochMillis = 0L,
            attemptCount = 5,
            lastAttemptAtEpochMillis = 1_000L,
            ownerAccountId = OWNER
        )
        // 新记录从未尝试过，必须立刻上传，不被旧记录的退避连坐。
        queue.items["fresh"] = PendingSessionUpload(
            record = record("fresh"),
            enqueuedAtEpochMillis = 0L,
            ownerAccountId = OWNER
        )
        val uploaded = mutableListOf<String>()
        val service = ApiSessionSyncService(queue, nowMillis = { 2_000L }) { request ->
            uploaded += request.records.map { it.clientRecordId }
            SessionBatchResult(request.records.map { SessionBatchItemResult(it.clientRecordId, true) })
        }

        service.flushPending(ownerAccountId = OWNER)

        assertEquals(listOf("fresh"), uploaded)
        assertEquals(listOf("blocked"), queue.items.keys.toList())
    }

    @Test
    fun uploadsOnlyRecordsOwnedByCurrentAccount() = runBlocking {
        val queue = InMemoryQueue()
        queue.enqueue(record("mine"), OWNER)
        queue.enqueue(record("someone-else"), "acc-2")
        val uploaded = mutableListOf<String>()
        val service = ApiSessionSyncService(queue) { request ->
            uploaded += request.records.map { it.clientRecordId }
            SessionBatchResult(request.records.map { SessionBatchItemResult(it.clientRecordId, true) })
        }

        service.flushPending(ownerAccountId = OWNER)

        assertEquals(listOf("mine"), uploaded)
        assertEquals(listOf("someone-else"), queue.items.keys.toList())
    }

    @Test
    fun batchWideValidationFailureFallsBackToPerRecord() = runBlocking {
        val queue = InMemoryQueue()
        queue.enqueue(record("good"), OWNER)
        queue.enqueue(record("bad"), OWNER)
        val service = ApiSessionSyncService(queue) { request ->
            if (request.records.size > 1) {
                throw httpError(422, "VALIDATION_FAILED")
            }
            val item = request.records.single()
            if (item.clientRecordId == "bad") {
                SessionBatchResult(
                    listOf(
                        SessionBatchItemResult(
                            item.clientRecordId,
                            accepted = false,
                            error = ApiError("VALIDATION_FAILED", "bad")
                        )
                    )
                )
            } else {
                SessionBatchResult(listOf(SessionBatchItemResult(item.clientRecordId, true)))
            }
        }

        val result = service.flushPending(ownerAccountId = OWNER)

        assertTrue(result is ApiResult.Success)
        // 坏记录进隔离区而不是删除，合法记录照常出队。
        assertTrue(queue.items.containsKey("bad"))
        assertEquals(UploadStatus.QUARANTINED, queue.items["bad"]!!.status)
        assertFalse(queue.items.containsKey("good"))
        assertEquals(1, queue.heldCount(OWNER))
    }

    @Test
    fun networkFailureDuringPerRecordFallbackKeepsRemaining() = runBlocking {
        val queue = InMemoryQueue()
        queue.enqueue(record("first"), OWNER)
        queue.enqueue(record("second"), OWNER)
        val service = ApiSessionSyncService(queue) { request ->
            if (request.records.size > 1) {
                throw httpError(422, "VALIDATION_FAILED")
            }
            throw IOException("offline")
        }

        val result = service.flushPending(ownerAccountId = OWNER)

        assertTrue(result is ApiResult.Failure)
        // 逐条回退遇到网络故障：当前与剩余条目都保留并进入退避，不丢记录。
        assertEquals(setOf("first", "second"), queue.items.keys)
        assertEquals(1, queue.items["first"]!!.attemptCount)
        assertEquals(1, queue.items["second"]!!.attemptCount)
    }

    @Test
    fun batchForbiddenDoesNotQuarantineEverything() = runBlocking {
        val queue = InMemoryQueue()
        queue.enqueue(record("a"), OWNER)
        queue.enqueue(record("b"), OWNER)
        var calls = 0
        val service = ApiSessionSyncService(queue) {
            calls++
            throw httpError(403, "FORBIDDEN")
        }

        val result = service.flushPending(ownerAccountId = OWNER)

        assertTrue(result is ApiResult.Failure)
        // 整批鉴权失败只做一次请求并整批退避，不能逐条把记录全打进隔离区。
        assertEquals(1, calls)
        assertEquals(0, queue.heldCount(OWNER))
        assertEquals(1, queue.items["a"]!!.attemptCount)
        assertEquals(1, queue.items["b"]!!.attemptCount)
    }

    private fun httpError(status: Int, code: String): HttpException = HttpException(
        Response.error<SessionBatchResult>(
            status,
            """{"code":"$code","message":"bad"}"""
                .toResponseBody("application/json".toMediaType())
        )
    )
}

class UploadBackoffPolicyTest {

    private fun item(attemptCount: Int, lastAttemptAt: Long?) = PendingSessionUpload(
        record = SessionRecordCreate(
            clientRecordId = "client-1",
            patientId = "patient-1",
            prescriptionId = "rx-1",
            recordedAt = "2026-11-07T02:00:00Z",
            modelVersion = "m",
            modelSha256 = "a".repeat(64),
            metricAlgorithmVersion = "alg",
            thresholdConfigVersion = "th"
        ),
        enqueuedAtEpochMillis = 0L,
        attemptCount = attemptCount,
        lastAttemptAtEpochMillis = lastAttemptAt
    )

    @Test
    fun delayGrowsExponentially() {
        assertEquals(0L, UploadBackoffPolicy.delayAfterFailure(0))
        assertEquals(30_000L, UploadBackoffPolicy.delayAfterFailure(1))
        assertEquals(60_000L, UploadBackoffPolicy.delayAfterFailure(2))
        assertEquals(120_000L, UploadBackoffPolicy.delayAfterFailure(3))
        assertEquals(240_000L, UploadBackoffPolicy.delayAfterFailure(4))
    }

    @Test
    fun delayCapsWithoutOverflow() {
        assertEquals(UploadBackoffPolicy.MAX_DELAY_MS, UploadBackoffPolicy.delayAfterFailure(7))
        // 直接按 Int.MAX_VALUE 位移会溢出成负数，必须仍返回正的上限。
        assertEquals(UploadBackoffPolicy.MAX_DELAY_MS, UploadBackoffPolicy.delayAfterFailure(Int.MAX_VALUE))
        // 负数当「从未失败」处理，不是当「无限久」。
        assertEquals(0L, UploadBackoffPolicy.delayAfterFailure(-3))
    }

    @Test
    fun neverAttemptedIsDueImmediately() {
        assertTrue(UploadBackoffPolicy.isDue(item(attemptCount = 0, lastAttemptAt = null), 0L))
    }

    @Test
    fun recentlyFailedIsNotDueUntilDelayElapses() {
        val failed = item(attemptCount = 1, lastAttemptAt = 1_000L)
        assertFalse(UploadBackoffPolicy.isDue(failed, nowEpochMillis = 30_999L))
        assertTrue(UploadBackoffPolicy.isDue(failed, nowEpochMillis = 31_000L))
    }

    @Test
    fun laterFailuresWaitLonger() {
        // 第 4 次失败后等 240s。
        val failed = item(attemptCount = 4, lastAttemptAt = 0L)
        assertFalse(UploadBackoffPolicy.isDue(failed, nowEpochMillis = 239_999L))
        assertTrue(UploadBackoffPolicy.isDue(failed, nowEpochMillis = 240_000L))
    }

    @Test
    fun clockRollbackDoesNotCauseRetryStorm() {
        // 设备时间被往回拨：now < lastAttempt，绝不能判成「已到期」。
        assertFalse(UploadBackoffPolicy.isDue(item(1, 1_000_000L), nowEpochMillis = 500_000L))
    }
}