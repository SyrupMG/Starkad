package ru.ctcmedia

import java.lang.ref.WeakReference
import kotlin.reflect.KClass

object Broadcaster {

    fun <T : Any> register(clz: KClass<T>, receiver: T) {
        registry[clz] = registry[clz] ?: mutableListOf()
        // skip if exists
        registry[clz]?.find { it.get() === receiver }?.run { return }
        registry[clz]!! += receiver.weak()
    }

    fun <T : Any> notify(clz: KClass<T>, block: T.() -> Unit) {
        cleanup()
        @Suppress("UNCHECKED_CAST")
        registry[clz]?.forEach { block(it.get() as T) }
    }

    fun <T : Any> unregister(clz: KClass<T>, receiver: T) {
        registry[clz]?.retainAll { it.get() !== receiver }
    }

    private fun cleanup() {
        registry.forEach { it.value.retainAll { it.get() != null } }
    }

    private val registry = mutableMapOf<KClass<*>, MutableList<WeakReference<*>>>()
}

inline fun <reified T : Any> Broadcaster.register(receiver: T) {
    register(T::class, receiver)
}

inline fun <reified T : Any> Broadcaster.unregister(receiver: T) {
    unregister(T::class, receiver)
}

inline fun <reified T : Any> Broadcaster.notify(noinline block: T.() -> Unit) {
    notify(T::class, block)
}