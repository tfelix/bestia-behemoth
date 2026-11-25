package net.bestia.zone.boot

import net.bestia.zone.worldevent.contract.ContributionJournal
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Loads who did what for the world events still running. In the "things about the world" group. */
@Component
@Order(6)
class ContributionBootRunner(
  private val journal: ContributionJournal,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    journal.loadAll()
  }
}
