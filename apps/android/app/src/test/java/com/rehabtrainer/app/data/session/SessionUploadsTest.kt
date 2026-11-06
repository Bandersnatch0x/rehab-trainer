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
}