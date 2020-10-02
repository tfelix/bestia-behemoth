package net.bestia.zoneserver.actor.socket

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class SocketMetricsReporter(
    registry: MeterRegistry
) {

  private val connections = registry.gauge("clientConnections", AtomicInteger(0))!!

  fun setConnectionCount(currentConnections: Int) {
    connections.set(currentConnections)
  }
}