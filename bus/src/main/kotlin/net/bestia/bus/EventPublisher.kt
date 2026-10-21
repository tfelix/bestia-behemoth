package net.bestia.bus

import net.bestia.bus.contract.ZoneEvent

/** A zone's way onto the event stream. Never blocks; returns false when the event had to be dropped. */
fun interface EventPublisher {
  fun publish(event: ZoneEvent): Boolean
}
