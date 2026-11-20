package net.bestia.zone.settlement

import net.bestia.zone.entity.StaticEntityKind

/**
 * Which player structures may stand anywhere and which only in a settlement. Numbers from the design docs'
 * "Where to Build" page.
 */
object BuildingPlaces {

  /** A town structure must stand this close to the city sign or to a building of the town. */
  const val ANCHOR_RANGE_METRES = 30.0

  /** Small camp and gathering structures. Everything not listed here needs a settlement, so a new kit does too. */
  val WILD_KINDS: Set<StaticEntityKind> = setOf(
    StaticEntityKind.CAMPFIRE,
    StaticEntityKind.MINING_SHAFT,
    StaticEntityKind.THEODOLITE,
    StaticEntityKind.CHEST,
    StaticEntityKind.WORKBENCH,
    StaticEntityKind.MANA_CIRCLE,
  )

  /** The city sign founds the settlement, so Founder checks its spot with rules of its own. */
  fun needsSettlement(kind: StaticEntityKind): Boolean {
    return kind !in WILD_KINDS && kind != StaticEntityKind.CITY_SIGN
  }
}
