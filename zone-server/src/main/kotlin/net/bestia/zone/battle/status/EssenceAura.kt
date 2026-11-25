package net.bestia.zone.battle.status

/** A boss essence that reaches past its wearer, every second, to those within [radiusMetres]. */
enum class EssenceAura(val radiusMetres: Long) {
  /** The party and the wearer's own Bestias attack faster. */
  ROYAL_SWARM(10),

  /** The wearer's own Bestias hit harder. */
  CAPTAINS_ORDERS(15),

  /** The wearer's own Bestias heal a little every few seconds. */
  BUDDING_BROOD(15),

  /** Foes close by may be poisoned. */
  STENCH(3),
}
