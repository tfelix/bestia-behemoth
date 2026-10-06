package net.bestia.zone.boot

import net.bestia.zone.world.stream.ChunkEditJournal
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Puts the terrain players dug back. In the "things about the world" group, after the world is loaded
 * (`@Order(1)`) and before the tick starts or logins open.
 */
@Component
@Order(8)
class ChunkEditBootRunner(
  private val journal: ChunkEditJournal,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    journal.restoreAll()
  }
}
