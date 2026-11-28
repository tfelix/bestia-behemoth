package net.bestia.zone.faction

import org.springframework.boot.context.properties.ConfigurationProperties

/** See the Factions page in the docs. */
@ConfigurationProperties(prefix = "faction")
data class FactionConfig(
  /** A master below this level cannot swear to an Order. */
  val minSwearLevel: Int = 40,
  /** How many mana crystals the Novitiate of Eternity or Chaos asks for. */
  val novitiateOfferings: Int = 3,
  val novitiateDays: Long = 30,
  val warZone: WarZone = WarZone(),
) {

  /** When rival kills open a war zone, how big it is and how long it stays open. Distances in metres. */
  data class WarZone(
    val radiusMetres: Double = 1000.0,
    val lingerMinutes: Long = 30,
    /** This many rival kills within [killWindowMinutes] and [killAreaMetres] of each other open a zone. */
    val kills: Int = 3,
    val killWindowMinutes: Long = 10,
    val killAreaMetres: Double = 300.0,
  )
}
