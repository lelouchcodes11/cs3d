package com.lagradost.cloudstream3.utils

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * A thread-safe list backed by [SynchronizedObject].
 *
 * Iterating it walks a copy taken under the lock, so a loop (forEach, filter, toList, firstOrNull...) never throws
 * ConcurrentModificationException while another thread adds to the list (extensions loading at the same time did,
 * and an extension failed to load). [withLock] still holds the lock for a block that must see one consistent list.
 */
open class AtomicList<T>(
    private val delegate: List<T> = emptyList(),
) : List<T>, SynchronizedObject() {

    fun <R> withLock(block: () -> R): R = synchronized(this) { block() }

    /** The elements at this moment, copied under the lock */
    fun snapshot(): List<T> = synchronized(this) { ArrayList(delegate) }

    fun filter(predicate: (T) -> Boolean): AtomicList<T> = synchronized(this) { AtomicList(delegate.filter(predicate)) }
    fun distinctBy(selector: (T) -> Any?): AtomicList<T> = synchronized(this) { AtomicList(delegate.distinctBy(selector)) }

    override val size: Int get() = synchronized(this) { delegate.size }
    override fun isEmpty(): Boolean = synchronized(this) { delegate.isEmpty() }
    override fun contains(element: T): Boolean = synchronized(this) { delegate.contains(element) }
    override fun containsAll(elements: Collection<T>): Boolean = synchronized(this) { delegate.containsAll(elements) }
    override fun get(index: Int): T = synchronized(this) { delegate[index] }
    override fun indexOf(element: T): Int = synchronized(this) { delegate.indexOf(element) }
    override fun lastIndexOf(element: T): Int = synchronized(this) { delegate.lastIndexOf(element) }

    override fun iterator(): Iterator<T> = snapshot().iterator()
    override fun listIterator(): ListIterator<T> = snapshot().listIterator()
    override fun listIterator(index: Int): ListIterator<T> = snapshot().listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<T> = synchronized(this) { ArrayList(delegate.subList(fromIndex, toIndex)) }

    operator fun plus(element: T): AtomicList<T> = synchronized(this) { AtomicList(delegate + element) }
    operator fun plus(elements: Collection<T>): AtomicList<T> = synchronized(this) { AtomicList(delegate + elements) }
}

class AtomicMutableList<T>(
    private val mutableDelegate: MutableList<T> = mutableListOf(),
) : AtomicList<T>(mutableDelegate), MutableList<T> {

    override fun add(element: T): Boolean = synchronized(this) { mutableDelegate.add(element) }
    override fun add(index: Int, element: T) = synchronized(this) { mutableDelegate.add(index, element) }
    override fun addAll(elements: Collection<T>): Boolean = synchronized(this) { mutableDelegate.addAll(elements) }
    override fun addAll(index: Int, elements: Collection<T>): Boolean = synchronized(this) { mutableDelegate.addAll(index, elements) }
    override fun remove(element: T): Boolean = synchronized(this) { mutableDelegate.remove(element) }
    override fun removeAt(index: Int): T = synchronized(this) { mutableDelegate.removeAt(index) }
    override fun removeAll(elements: Collection<T>): Boolean = synchronized(this) { mutableDelegate.removeAll(elements) }
    override fun retainAll(elements: Collection<T>): Boolean = synchronized(this) { mutableDelegate.retainAll(elements) }
    override fun set(index: Int, element: T): T = synchronized(this) { mutableDelegate.set(index, element) }
    override fun clear() = synchronized(this) { mutableDelegate.clear() }

    /** Removes everything [predicate] matches in one step under the lock (the default walked an iterator, one remove at a time) */
    fun removeAll(predicate: (T) -> Boolean): Boolean = synchronized(this) { mutableDelegate.removeAll(predicate) }

    /** Walks a copy; remove() takes the element just returned out of the real list */
    override fun iterator(): MutableIterator<T> = object : MutableIterator<T> {
        private val it = snapshot().iterator()
        private var last: Any? = NONE
        override fun hasNext() = it.hasNext()
        override fun next(): T = it.next().also { last = it }
        override fun remove() {
            check(last !== NONE) { "next() first" }
            @Suppress("UNCHECKED_CAST") remove(last as T)
            last = NONE
        }
    }

    // a list iterator that changes the list (set/add at a position) needs the real list: hold withLock { } around such a loop
    override fun listIterator(): MutableListIterator<T> = mutableDelegate.listIterator()
    override fun listIterator(index: Int): MutableListIterator<T> = mutableDelegate.listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = mutableDelegate.subList(fromIndex, toIndex)

    private companion object { val NONE = Any() }
}
