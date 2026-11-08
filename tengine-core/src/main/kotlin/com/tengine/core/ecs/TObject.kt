package com.tengine.core.ecs

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Base identifiable object for all T-Engine entities, components, and assets (modeled after UnityEngine.Object).
 */
abstract class TObject(
    var name: String = "Object",
    uuid: String? = null
) {
    val instanceId: Long = NEXT_INSTANCE_ID.getAndIncrement()
    var uuid: String = uuid ?: UUID.randomUUID().toString()
        internal set

    var isDestroyed: Boolean = false
        internal set

    companion object {
        private val NEXT_INSTANCE_ID = AtomicLong(1L)
    }
}
