package net.bestia.zone.townsfolk.knowledge

import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.CultKind
import net.bestia.worldgen.core.CultRecord
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.world.SettlementLoreService
import kotlin.math.hypot

/**
 * Whose golden stone an Empyrite Seed is, as its legend tells it: the god or cult of the nearest sanctuary, else the
 * god of the nearest town's temple, else the god of that town's people. The god goes by the name those people give
 * it, because they are the ones who named the seed.
 */
object SeedPatrons {

  /** A legend about the seed: its [kind], the [year] its patron came to be, and the patron as a [slot]. */
  class Patron(val kind: LegendKind, val year: Int, val slot: Pair<String, Knowledge.Slot>)

  fun of(chronicle: Chronicle, seed: EmpyriteSeed): Patron {
    val culture = ChronicleNames.cultureOfSettlement(chronicle, seed.settlement)

    nearestSanctuaryOf(chronicle, seed)?.let { cult -> return ofCult(chronicle, cult, culture) }

    val town = chronicle.settlements.getOrNull(seed.settlement)
    val townGod = town?.patron?.takeIf { it >= 0 }
    val peoplesGod = town?.let { it.ownerCiv.takeIf { civ -> civ >= 0 } ?: it.foundingCiv }
      ?.let { chronicle.civs.getOrNull(it)?.patron }
      ?.takeIf { it >= 0 }

    return ofGod(chronicle, townGod ?: peoplesGod ?: -1, culture)
  }

  private fun nearestSanctuaryOf(chronicle: Chronicle, seed: EmpyriteSeed): CultRecord? {
    return chronicle.cults
      .mapNotNull { cult -> chronicle.sites.getOrNull(cult.sanctuary)?.let { cult to it.position } }
      .map { (cult, at) -> cult to hypot(at.x - seed.x, at.y - seed.y) }
      .filter { (_, distance) -> distance <= SANCTUARY_REACH }
      .minByOrNull { (_, distance) -> distance }
      ?.first
  }

  private fun ofCult(chronicle: Chronicle, cult: CultRecord, culture: Int): Patron {
    if (cult.kind == CultKind.DEITY && cult.deity >= 0) {
      return ofGod(chronicle, cult.deity, culture, year = cult.foundedYear)
    }
    val name = ChronicleNames.cultOf(chronicle, cult.index)
    val slot = name?.let { Knowledge.Slot.Name(it) } ?: Knowledge.Slot.Token(HistoryKnowledge.UNKNOWN_CULT)

    return Patron(LegendKind.EMPYRITE_CULT, cult.foundedYear, HistoryKnowledge.SLOT_CULT to slot)
  }

  /** [deity] -1 is a god nobody names any more: a seed with no sanctuary, temple or people near it. */
  private fun ofGod(chronicle: Chronicle, deity: Int, culture: Int, year: Int? = null): Patron {
    val name = ChronicleNames.deityOf(chronicle, deity, culture)
    val slot = name?.let { Knowledge.Slot.Name(it) } ?: Knowledge.Slot.Token(HistoryKnowledge.UNKNOWN_DEITY)
    val since = year ?: chronicle.deities.getOrNull(deity)?.year ?: 0

    return Patron(LegendKind.EMPYRITE_GOD, since, HistoryKnowledge.SLOT_DEITY to slot)
  }

  /** As far as a town's own memories reach: a sanctuary further off is somebody else's story. */
  private const val SANCTUARY_REACH = SettlementLoreService.NEARBY_RANGE
}
