package net.bestia.zone.world.mana

/** Where the land drains added mana: the provinces of the start towns. */
interface ManaSanctuaries {

  /** Whether the world position ([x], [y]) in metres lies in a sanctuary. */
  fun isSanctuaryAt(x: Double, y: Double): Boolean
}
