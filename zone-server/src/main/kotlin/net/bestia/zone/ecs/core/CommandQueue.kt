package net.bestia.zone.ecs.core

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KClass

/**
 * Thread-safe inbound command queue with per-type handler dispatch.
 *
 * Producers (any thread) call [enqueue]; the queue is drained once at the start
 * of each tick on the tick thread, so handlers run inside the simulation and may
 * freely mutate ECS state. This mirrors the handler-by-`KClass` pattern of the
 * existing `InMessageProcessor` so it should feel familiar.
 */
class CommandQueue {
  private val queue = ConcurrentLinkedQueue<Command>()
  private val handlers = HashMap<KClass<out Command>, MutableList<(World, Command) -> Unit>>()

  /** Enqueue a command from any thread. */
  fun enqueue(command: Command) {
    queue.add(command)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T : Command> on(type: KClass<T>, handler: (World, T) -> Unit) {
    handlers.getOrPut(type) { ArrayList() }.add(handler as (World, Command) -> Unit)
  }

  /** Drains all pending commands and dispatches them. Tick thread only. */
  fun drain(world: World) {
    while (true) {
      val command = queue.poll() ?: break
      handlers[command::class]?.forEach { it(world, command) }
    }
  }
}
