package net.bestia.zone.townsfolk.knowledge

import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.history.NameLocale
import net.bestia.worldgen.history.Names

/**
 * What the chronicle's entities are called, and in whose idiom.
 *
 * The join between a record's name seed and the culture that named it is small but easy to get subtly
 * wrong, so it lives in one place rather than at each call site.
 */
object ChronicleNames {

  fun placeOf(chronicle: Chronicle, settlement: Int): String? {
    val record = chronicle.settlements.getOrNull(settlement) ?: return null

    return Names.place(record.nameSeed, record.nameCulture)
  }

  fun civOf(chronicle: Chronicle, civ: Int, locale: NameLocale = NameLocale.EN): String? {
    val record = chronicle.civs.getOrNull(civ) ?: return null

    return Names.civ(record.nameSeed, record.cultureIndex, locale)
  }

  fun figureOf(chronicle: Chronicle, figure: Int, locale: NameLocale = NameLocale.EN): String? {
    val record = chronicle.figures.getOrNull(figure) ?: return null

    return Names.person(record.nameSeed, cultureOfCiv(chronicle, record.civ), record.role, locale)
  }

  fun artifactOf(chronicle: Chronicle, artifact: Int, locale: NameLocale = NameLocale.EN): String? {
    val record = chronicle.artifacts.getOrNull(artifact) ?: return null
    val culture = cultureOfCiv(chronicle, chronicle.figures.getOrNull(record.forgedBy)?.civ ?: -1)

    return Names.artifact(record.nameSeed, culture, record.kind, record.forgedAtNameSeed, locale)
  }

  /** A god as one people calls it. Every culture renders the same seed, so one god has a name per people. */
  fun deityOf(chronicle: Chronicle, deity: Int, culture: Int): String? {
    val record = chronicle.deities.getOrNull(deity) ?: return null

    return Names.deity(record.nameSeed, culture, record.domain)
  }

  /** A ruling house as its bare name: the phrasing says "House" in its own language. */
  fun houseOf(chronicle: Chronicle, dynasty: Int): String? {
    val record = chronicle.dynasties.getOrNull(dynasty) ?: return null

    return Names.houseStem(record.nameSeed, cultureOfCiv(chronicle, record.civ))
  }

  /** A world boss: its own name and its species' title, the same for every people. */
  fun monsterOf(chronicle: Chronicle, monster: Int): String? {
    val record = chronicle.monsters.getOrNull(monster) ?: return null

    return Names.monster(record.nameSeed, record.boss)
  }

  /** A cult, named in the idiom of the town that founded it. */
  fun cultOf(chronicle: Chronicle, cult: Int): String? {
    val record = chronicle.cults.getOrNull(cult) ?: return null
    val culture = cultureOfSettlement(chronicle, record.origin)

    return Names.cult(record.nameSeed, record.kind, deityOf(chronicle, record.deity, culture), record.beast)
  }

  /**
   * Whose idiom the people of a town speak: whoever holds it, or whoever founded it once nobody does.
   *
   * Not the idiom of the town's own name, which is [net.bestia.worldgen.core.SettlementRecord.nameCulture].
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
