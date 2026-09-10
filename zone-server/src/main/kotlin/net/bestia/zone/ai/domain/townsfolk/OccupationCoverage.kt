package net.bestia.zone.ai.domain.townsfolk

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.pop.BusinessCatalogue
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Checks the occupations against the trades the world generator actually builds, in both directions.
 *
 * Both directions, because the two failures are equally bad and only one of them is obvious. An
 * occupation naming a trade that does not exist is a townsperson who can never be given a workplace. A
 * trade with nobody to keep it is a shop standing in every town in the world with the lights off - which
 * looks like nothing at all going wrong, and is the reason the second half is worth a boot failure rather
 * than a log line.
 *
 * The second half is satisfiable today only because a trade may be listed as deliberately unstaffed. That
 * list is the release's own to-do, in a form the build enforces: every occupation added later has to take
 * a name off it, and a new trade added to `BusinessCatalogue` fails the boot until somebody decides which
 * side it is on.
 *
 * On [ApplicationReadyEvent] rather than at construction because it needs a catalogue the boot fills in
 * later - the item, recipe and mob importers are `CommandLineRunner`s, and this will grow to check against
 * them as occupations gain recipes.
 */
@Component
class OccupationCoverage(private val occupations: OccupationCatalogue) {

  @EventListener(ApplicationReadyEvent::class)
  fun check() {
    val trades = BusinessCatalogue.ALL.map { it.id }.toSet()
    val unstaffed = occupations.unstaffedTrades()

    val unknownTrades = occupations.all().mapNotNull { it.businessType }.filterNot { it in trades }
    require(unknownTrades.isEmpty()) {
      "Occupations name trades that BusinessCatalogue does not have: ${unknownTrades.sorted()}"
    }

    val unknownUnstaffed = unstaffed.filterNot { it in trades }
    require(unknownUnstaffed.isEmpty()) {
      "The unstaffed list names trades that BusinessCatalogue does not have: ${unknownUnstaffed.sorted()}"
    }

    val staffed = occupations.all().mapNotNull { it.businessType }.toSet()
    val bothWays = staffed intersect unstaffed
    require(bothWays.isEmpty()) {
      "These trades are both staffed and listed as unstaffed: ${bothWays.sorted()}"
    }

    val orphaned = trades - staffed - unstaffed
    require(orphaned.isEmpty()) {
      "These trades have nobody to keep them and are not listed as deliberately unstaffed, so every town " +
        "in the world would build them and leave them empty: ${orphaned.sorted()}"
    }

    LOG.info { "Occupations cover ${staffed.size} of ${trades.size} trades; ${unstaffed.size} are still empty" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
