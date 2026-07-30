package net.bestia.zone.boot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.world.MasterSpawnPointService
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Computes (or loads the cached) settlement spawn point candidates for the world
 * [WorldGenerationBootRunner] just loaded.
 *
 * Ordered right after it (`@Order(1)`) so [net.bestia.zone.world.WorldService.generated] is available,
 * and before [EntityLoaderBootRunner] (`@Order(110)`) so the ward-entity persister can rely on the
 * candidates already being in the database instead of computing them itself.
 */
@Component
@Order(2)
class SettlementSpawnPointBootRunner(
  private val masterSpawnPointService: MasterSpawnPointService,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    val points = masterSpawnPointService.ensureComputed()
    LOG.info { "${points.size} master spawn point candidate(s) ready: ${points.joinToString { it.settlementName }}" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
