package net.bestia.zone.boot

import net.bestia.zone.doomsday.withering.CorruptionJournal
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Loads the Withering's corruption, beside the mana field and for the same reason: the version stamps. */
@Component
@Order(6)
class CorruptionBootRunner(
  private val journal: CorruptionJournal,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    journal.loadAll()
  }
}
