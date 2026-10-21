package net.bestia.bus

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusRequest

/** A zone's answer to one request. Runs on a bus worker thread, never a Valkey I/O thread; it may block. */
fun interface RequestHandler {
  fun handle(request: BusRequest): BusReply
}
