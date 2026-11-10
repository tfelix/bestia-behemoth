package net.bestia.zone.prop

/** Which holders keep a [WorldObjectSource]'s entities in the world. */
enum class WorldObjectScope {

  /**
   * A client that holds the column, or a gatherer whose area touches it. Generated props are far too many to load
   * around every anchor.
   */
  VIEWED,

  /**
   * Any holder in [net.bestia.zone.world.residency.ChunkResidency], so roaming NPCs reach it with nobody watching.
   * Its source is asked about every column around every anchor, so it must answer from memory.
   */
  SIMULATED
}
