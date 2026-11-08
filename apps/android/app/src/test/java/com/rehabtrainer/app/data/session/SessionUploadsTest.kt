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
import java.io.IOException
import kotlinx.coroutines.runBlocking
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

        assertTrue(queue.enqueue(record("client-1")))
        assertFalse(queue.enqueue(record("client-1")))
        assertEquals(1, queue.pendingCount())

        // 新实例读取同一文件，模拟进程重启。
        val reopened = FileSessionUploadQueue(root)
        assertEquals(1, reopened.pendingCount())
        assertEquals("client-1", reopened.pending().first().clientRecordId)
    }

    @Test
    fun markUploadedRemovesAndMarkFailedKeeps() = runBlocking {
        val queue = FileSessionUploadQueue(folder.newFolder("queue2"))
        queue.enqueue(record("client-1"))
        queue.enqueue(record("client-2"))

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
}

class ApiSessionSyncServiceTest {

    private class InMemoryQueue : SessionUploadQueue {
        val items = linkedMapOf<String, PendingSessionUpload>()

        override suspend fun enqueue(record: SessionRecordCreate): Boolean =
            items.putIfAbsent(
                record.clientRecordId,
                PendingSessionUpload(record, enqueuedAtEpochMillis = 0L)
            ) == null

        override suspend fun pending(limit: Int): List<PendingSessionUpload> =
            items.values.take(limit)

        override suspend fun markUploaded(clientRecordIds: Collection<String>) {
            clientRecordIds.forEach { items.remove(it) }
        }

        override suspend fun markFailed(clientRecordIds: Collection<String>, error: ApiError) {
            clientRecordIds.forEach { id ->
                items[id]?.let { items[id] = it.copy(attemptCount = it.attemptCount + 1) }
            }
        }

        override suspend fun remove(clientRecordIds: Collection<String>) {
            clientRecordIds.forEach { items.remove(it) }
        }

        override suspend fun pendingCount(): Int = items.size
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
        queue.enqueue(record("client-1"))
        val service = ApiSessionSyncService(queue) { request ->
            SessionBatchResult(request.records.map { SessionBatchItemResult(it.clientRecordId, true) })
        }

        val result = service.flushPending()
        assertTrue(result is ApiResult.Success)
        assertEquals(0, queue.pendingCount())
    }

    @Test
    fun retryableRejectionStaysAndNonRetryableLeaves() = runBlocking {
        val queue = InMemoryQueue()
        queue.enqueue(record("retry"))
        queue.enqueue(record("reject"))
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

        service.flushPending()

        assertEquals(listOf("retry"), queue.items.keys.toList())
        assertEquals(1, queue.items["retry"]!!.attemptCount)
    }

    @Test
    fun uploadFailureKeepsQueue() = runBlocking {
        val queue = InMemoryQueue()
        queue.enqueue(record("client-1"))
        val service = ApiSessionSyncService(queue) { throw IOException("offline") }

        val result = service.flushPending()

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
            lastAttemptAtEpochMillis = 1_000L
        )
        var calls = 0
        val service = ApiSessionSyncService(queue, nowMillis = { 11_000L }) {
            calls++
            SessionBatchResult(emptyList())
        }

        val result = service.flushPending()

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
            lastAttemptAtEpochMillis = 1_000L
        )
        // 新记录从未尝试过，必须立刻上传，不被旧记录的退避连坐。
        queue.items["fresh"] = PendingSessionUpload(
            record = record("fresh"),
            enqueuedAtEpochMillis = 0L
        )
        val uploaded = mutableListOf<String>()
        val service = ApiSessionSyncService(queue, nowMillis = { 2_000L }) { request ->
            uploaded += request.records.map { it.clientRecordId }
            SessionBatchResult(request.records.map { SessionBatchItemResult(it.clientRecordId, true) })
        }

        service.flushPending()

        assertEquals(listOf("fresh"), uploaded)
        assertEquals(listOf("blocked"), queue.items.keys.toList())
    }
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