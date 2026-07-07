package net.bestia.zone.boot

import net.bestia.zone.engine.ZoneEngine
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * As the last step of the boot sequence we start the zone.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
class WorldBootRunner(
  private val zoneEngine: ZoneEngine
) : ApplicationRunner {
  override fun run(args: ApplicationArguments?) {
    zoneEngine.start()
  }
}
