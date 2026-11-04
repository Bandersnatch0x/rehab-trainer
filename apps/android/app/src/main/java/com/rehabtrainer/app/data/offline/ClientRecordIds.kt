package com.rehabtrainer.app.data.offline

import java.util.UUID

/**
 * 训练记录幂等键生成器。
 *
 * `clientRecordId` 在设备离线产生记录时生成；同一条记录的重试、补传必须复用同一个值，
 * 否则服务端无法按契约去重。只有一次全新的训练才生成新 ID。
 */
object ClientRecordIds {

    fun newId(): String = UUID.randomUUID().toString()
}
