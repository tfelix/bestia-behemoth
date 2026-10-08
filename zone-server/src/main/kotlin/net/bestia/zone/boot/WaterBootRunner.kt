package net.bestia.zone.boot

import net.bestia.zone.water.WaterJournal
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Carries on the water that was still moving when the server stopped. After [ChunkEditBootRunner] (`@Order(8)`),
 * because the simulation reads its chunks from the restored edits.
 */
@Component
@Order(9)
class WaterBootRunner(
  private val journal: WaterJournal,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    journal.restoreAll()
  }
}
