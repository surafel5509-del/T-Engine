package com.tengine.core.ecs

import com.tengine.core.utils.CoroutineScheduler

/**
 * Unity MonoBehaviour equivalent for gameplay scripts in Kotlin.
 * Includes built-in game-time timer/coroutine helpers automatically cancelled on destroy!
 */
abstract class Behaviour(scriptName: String = "Behaviour") : Component(scriptName) {

    fun invokeDelayed(delaySeconds: Float, action: () -> Unit): CoroutineScheduler.ScheduledTask? {
        val sc = scene?.scheduler ?: return null
        return sc.scheduleOnce(delaySeconds, ownerTag = this) {
            if (isActiveAndEnabled) action()
        }
    }

    fun invokeRepeating(
        initialDelaySeconds: Float,
        intervalSeconds: Float,
        repeatCount: Int = -1,
        action: () -> Unit
    ): CoroutineScheduler.ScheduledTask? {
        val sc = scene?.scheduler ?: return null
        return sc.scheduleRepeating(initialDelaySeconds, intervalSeconds, repeatCount, ownerTag = this) {
            if (isActiveAndEnabled) action()
        }
    }

    fun waitUntil(predicate: () -> Boolean, action: () -> Unit): CoroutineScheduler.ScheduledTask? {
        val sc = scene?.scheduler ?: return null
        return sc.waitUntil(
            predicate = { isDestroyed || (isActiveAndEnabled && predicate()) },
            ownerTag = this
        ) {
            if (isActiveAndEnabled) action()
        }
    }

    fun cancelInvokes() {
        scene?.scheduler?.cancelByOwner(this)
    }

    override fun onDestroy() {
        cancelInvokes()
        super.onDestroy()
    }
}
