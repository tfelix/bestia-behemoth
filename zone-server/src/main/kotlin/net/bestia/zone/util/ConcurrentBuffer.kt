package net.bestia.zone.util

import java.util.concurrent.ConcurrentLinkedQueue

abstract class ConcurrentBuffer<T> {
  private val queue = ConcurrentLinkedQueue<T & Any>()

  fun add(message: T) {
    queue.add(message)
  }

  fun pop(): T? {
    return queue.poll()
  }

  fun size(): Int {
    return queue.size
  }

  fun hasNext(): Boolean {
    return queue.isNotEmpty()
  }
}