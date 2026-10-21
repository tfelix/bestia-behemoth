package net.bestia.zone.companion

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import jakarta.annotation.PreDestroy
import net.bestia.bus.valkey.ValkeyRequestServer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Starts answering the bus once the zone is fully up, so no question meets a world that is still being generated. */
@Component
@ConditionalOnProperty(prefix = "bus", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class BusRequestServer(
  private val busClient: RedisClient,
  private val properties: BusProperties,
  private val dispatcher: CompanionRequestDispatcher,
) {

  @Volatile
  private var server: ValkeyRequestServer? = null

  @EventListener(ApplicationReadyEvent::class)
  fun start() {
    server = ValkeyRequestServer(busClient, properties.nodeId, dispatcher, properties.maxInFlight)
    LOG.info { "Answering the bus as node '${properties.nodeId}'" }
  }

  @PreDestroy
  fun stop() {
    server?.close()
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
