package net.bestia.zoneserver.socket

import java.util.*

class LatencyProvider {
  private val buffer = LinkedList<Int>()

  fun addLatency(ms: Int) {
    if (buffer.size > LATENCY_BUFFER_SIZE) {
      buffer.pop()
    }
    buffer.push(ms)
  }

  fun getLatency(): Int {
    return buffer.sum() / LATENCY_BUFFER_SIZE
  }

  companion object {
    private const val LATENCY_BUFFER_SIZE = 5
  }
}