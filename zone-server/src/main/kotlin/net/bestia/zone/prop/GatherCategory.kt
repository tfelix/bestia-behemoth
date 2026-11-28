package net.bestia.zone.prop

/** The kinds of things a gathering bestia's orders can tick off, by what it takes them from. */
enum class GatherCategory {
  /** Sticks, resin, loose stones, reeds: picked up from the ground. */
  LOOSE,

  /** Herbs and shrubs. */
  PLANT,

  /** Trees, felled for their logs and resin. */
  TREE,

  /** Mana and aetherite crystals. */
  CRYSTAL,
}
