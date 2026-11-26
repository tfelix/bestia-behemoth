package net.bestia.zone.boot

import net.bestia.zone.socket.ZoneReadinessService
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.WorldRebirth
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Flips the zone into the "ready" state once all preceding boot steps (entity load, and any future
 * world generation) have completed. Ordered just before the world tick loop and socket bind so
 * client logins are accepted only after the world is fully populated. Any slow future startup step
 * that must hold the zone closed should be ordered before this runner.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 2)
class ZoneReadyBootRunner(
  private val zoneReadinessService: ZoneReadinessService,
  private val rebirth: WorldRebirth,
) : ApplicationRunner {
  override fun run(args: ApplicationArguments?) {
    zoneReadinessService.markReady()
    // A failure leaves logins refused, so the zone says so loudly rather than refusing to start.
    runCatching { rebirth.end() }.onFailure { LOG.error(it) { "Could not tell the login server the world is ready" } }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
