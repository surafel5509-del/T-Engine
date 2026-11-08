package com.tengine.core.utils

import kotlin.reflect.KClass

/**
 * Strongly-typed decoupled publish/subscribe EventBus for gameplay, UI, and achievement events.
 */
class EventBus {
    fun interface Subscription {
        fun unsubscribe()
    }

    private val handlers = LinkedHashMap<KClass<*>, MutableList<(Any) -> Unit>>()

    fun <T : Any> subscribe(eventType: KClass<T>, handler: (T) -> Unit): Subscription {
        val wrapper: (Any) -> Unit = { raw ->
            @Suppress("UNCHECKED_CAST")
            handler(raw as T)
        }
        val list = handlers.getOrPut(eventType) { mutableListOf() }
        list.add(wrapper)
        return Subscription {
            handlers[eventType]?.remove(wrapper)
        }
    }

    inline fun <reified T : Any> on(noinline handler: (T) -> Unit): Subscription =
        subscribe(T::class, handler)

    fun <T : Any> publish(event: T) {
        val snapshot = handlers[event::class]?.toList() ?: return
        for (handler in snapshot) {
            handler(event)
        }
    }

    fun clear() {
        handlers.clear()
    }
}
