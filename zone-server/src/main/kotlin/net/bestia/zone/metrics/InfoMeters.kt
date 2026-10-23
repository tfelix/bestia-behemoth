package net.bestia.zone.metrics

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.zone.world.WorldService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * A gauge that is always 1 and carries what this process is in its tags: the build, the protocol it speaks and
 * the versions of the world it loaded. A dashboard joins it onto anything else, so a change in behaviour can be
 * laid next to the deploy that caused it, and a zone running a different world than its neighbour shows.
 *
 * Made once the zone is ready, because the world's record does not exist before it is loaded. The shard is not
 * a tag of its own: every meter already carries it.
 */
@Component
class InfoMeters(
  private val registry: MeterRegistry,
  private val worlds: WorldService,
  @Value("\${zone.version}") private val version: String,
) {

  @EventListener(ApplicationReadyEvent::class)
  fun bind() {
    if (!worlds.isLoaded) {
      LOG.debug { "No world is loaded, so there is no zone info to export" }
      return
    }

    val world = worlds.record

    Gauge.builder("zone.info", this) { 1.0 }
      .tag("version", version)
      .tag("protocol", AuthenticationProto.ProtocolVersion.PROTOCOL_VERSION_CURRENT.number.toString())
      .tag("pipeline", world.pipelineVersion.toString())
      .tag("palette", world.blockPaletteVersion.toString())
      .tag("chunk_format", world.chunkFormatVersion.toString())
      .tag("shape", world.shapeVersion.toString())
      .description("Always 1; what this zone is, in its tags")
      .strongReference(true)
      .register(registry)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
