package net.bestia.zone.boot

import net.bestia.zone.world.mana.ManaJournal
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Loads the mana play has added or taken, so it survives a restart. In the "things about the world" group,
 * after [WorldGenerationBootRunner] so the version stamps can be checked.
 */
@Component
@Order(6)
class ManaBootRunner(
  private val journal: ManaJournal,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    journal.loadAll()
  }
}
