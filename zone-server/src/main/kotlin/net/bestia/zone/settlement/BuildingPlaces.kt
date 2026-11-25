package net.bestia.zone.settlement

import net.bestia.zone.entity.StaticEntityKind

/**
 * Which player structures may stand anywhere and which only in a settlement. Numbers from the design docs'
 * "Where to Build" page.
 */
object BuildingPlaces {

  /** A town structure must stand this close to the city sign or to a building of the town. */
  const val ANCHOR_RANGE_METRES = 30.0

  /** Outside a settlement, one master's structures keep this far from another's. */
  const val WILD_SPACING_METRES = 10.0

  /** Small camp and gathering structures. Everything not listed here needs a settlement, so a new kit does too. */
  val WILD_KINDS: Set<StaticEntityKind> = setOf(
    StaticEntityKind.CAMPFIRE,
    StaticEntityKind.MINING_SHAFT,
    StaticEntityKind.THEODOLITE,
    StaticEntityKind.CHEST,
    StaticEntityKind.WORKBENCH,
    StaticEntityKind.MANA_CIRCLE,
    StaticEntityKind.OBSERVATION_OUTPOST,
    // A Stronghold stands at an Empyrite Seed, which lies at least 1 km from any town.
    StaticEntityKind.EMPYRITE_SITE,
  )

  /** The city sign founds the settlement, so Founder checks its spot with rules of its own. */
  fun needsSettlement(kind: StaticEntityKind): Boolean {
    return kind !in WILD_KINDS && kind != StaticEntityKind.CITY_SIGN
  }

  /** A finished town building the next one may stand near. A wild one is not: a lone workbench starts no town. */
  fun anchorsTown(kind: StaticEntityKind): Boolean {
    return needsSettlement(kind) && TownGrowth.pointsOf(kind) > 0.0
  }
}
