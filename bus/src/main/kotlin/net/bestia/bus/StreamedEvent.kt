package net.bestia.bus

import net.bestia.bus.contract.ZoneEvent

/** An event as read back from the stream. [id] is the stream entry id, which orders events and lets a reader resume. */
data class StreamedEvent(val id: String, val event: ZoneEvent)
