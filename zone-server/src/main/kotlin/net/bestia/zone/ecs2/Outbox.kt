package net.bestia.zone.ecs2

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe outbound event queue for arbitrary domain events emitted by
 * systems (e.g. death, loot dropped, level-up). Systems call [emit] on the tick
 * thread; an external consumer (typically the network layer) [drain]s it after
 * the tick and turns events into outgoing messages.
 *
 * Complements [ChangeTracker]: use component-change tracking for "this component
 * updated" sync, and the outbox for discrete gameplay events.
 */
class Outbox {
  private val queue = ConcurrentLinkedQueue<Any>()

  fun emit(event: Any) {
    queue.add(event)
  }

  fun drain(action: (Any) -> Unit) {
    while (true) {
      val event = queue.poll() ?: break
      action(event)
    }
  }

  fun isEmpty(): Boolean = queue.isEmpty()
}
