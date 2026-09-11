package net.bestia.zone.ai.knowledge

import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.history.Names

/**
 * What the chronicle's entities are called, and in whose idiom.
 *
 * The join between a record's name seed and the culture that named it is small but easy to get subtly
 * wrong - a ruin keeps the name the people who built it gave it, not the name of whoever holds the
 * ground now - so it lives in one place rather than at each call site.
 */
object ChronicleNames {

  fun placeOf(chronicle: Chronicle, settlement: Int): String? {
    val record = chronicle.settlements.getOrNull(settlement) ?: return null

    return Names.place(record.nameSeed, cultureOfSettlement(chronicle, settlement))
  }

  fun civOf(chronicle: Chronicle, civ: Int): String? {
    val record = chronicle.civs.getOrNull(civ) ?: return null

    return Names.civ(record.nameSeed, record.cultureIndex)
  }

  fun figureOf(chronicle: Chronicle, figure: Int): String? {
    val record = chronicle.figures.getOrNull(figure) ?: return null

    return Names.person(record.nameSeed, cultureOfCiv(chronicle, record.civ), record.role)
  }

  fun artifactOf(chronicle: Chronicle, artifact: Int): String? {
    val record = chronicle.artifacts.getOrNull(artifact) ?: return null
    val culture = cultureOfCiv(chronicle, chronicle.figures.getOrNull(record.forgedBy)?.civ ?: -1)

    return Names.artifact(record.nameSeed, culture, record.kind, record.forgedAtNameSeed)
  }

  /**
   * Whose idiom names a town: whoever holds it, or whoever founded it once nobody does.
   *
   * A ruin keeps the name the people who built it gave it, which is the whole reason `foundingCiv`
   * outlives `ownerCiv` on the record.
   */
  fun cultureOfSettlement(chronicle: Chronicle, settlement: Int): Int {
    val record = chronicle.settlements.getOrNull(settlement) ?: return -1
    val civ = record.ownerCiv.takeIf { it >= 0 } ?: record.foundingCiv

    return cultureOfCiv(chronicle, civ)
  }

  fun cultureOfCiv(chronicle: Chronicle, civ: Int): Int {
    return chronicle.civs.getOrNull(civ)?.cultureIndex ?: -1
  }
}
