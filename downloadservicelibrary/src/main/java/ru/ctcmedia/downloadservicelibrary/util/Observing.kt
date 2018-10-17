package ru.ctcmedia.downloadservicelibrary.util

import kotlin.properties.ObservableProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Created by horovodovodo4ka on 23.08.17.
 * Copyrights SL-Tech 2017
 */
private object EMPTY

abstract class LazyObservableProperty<T : Any?>(private val initialValue: () -> T) : ReadWriteProperty<Any?, T> {
    private var value: Any? = EMPTY

    protected abstract fun beforeChange(property: KProperty<*>, oldValue: T, newValue: T): Boolean
    protected abstract fun afterChange(property: KProperty<*>, oldValue: T, newValue: T)

    private fun calcVal(): T {
        if (value == EMPTY) {
            value = initialValue()
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return calcVal()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val oldValue = calcVal()
        if (!beforeChange(property, oldValue, value)) {
            return
        }
        this.value = value
        afterChange(property, oldValue, value)
    }
}

fun <T> observing(initialValue: T,
    willSet: () -> Unit = { },
    didSet: () -> Unit = { }
) = object : ObservableProperty<T>(initialValue) {
    override fun beforeChange(property: KProperty<*>, oldValue: T, newValue: T): Boolean = true.apply { willSet() }
    override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) = didSet()
}

fun <T> lazyObserving(initialValue: () -> T = { throw NotImplementedError() },
    willSet: () -> Unit = {  },
    didSet: () -> Unit = { }
) = object : LazyObservableProperty<T>(initialValue) {
    override fun beforeChange(property: KProperty<*>, oldValue: T, newValue: T): Boolean = true.apply { willSet() }
    override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) = didSet()
}