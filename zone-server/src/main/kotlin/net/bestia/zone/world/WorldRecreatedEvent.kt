package net.bestia.zone.world

import net.bestia.zone.world.persistence.PersistedWorld

/**
 * Published once the terrain has been rebuilt: after the doomsday ended the old world, or after
 * [WorldGenConfig.OnMismatch.REGENERATE] threw it away.
 *
 * Anything holding a coordinate into the old world is now holding a coordinate into nothing. An event rather
 * than direct calls because the things that need to hear it - masters, later any persisted structures - are
 * not the world module's to know about, and because it must fire *after* the new terrain exists so a listener
 * can ask where the new spawn is.
 */
data class WorldRecreatedEvent(val world: PersistedWorld, val cause: Cause) {

  /** A new world after the old one ended: players keep only what the carry-over allows. */
  val isRebirth: Boolean
    get() = cause == Cause.WORLD_ENDED

  enum class Cause {
    /** The doomsday ended the old world. What players keep into the new one is carried over. */
    WORLD_ENDED,

    /** The stored world no longer matched this build or its settings. Players keep everything. */
    MISMATCH,
  }

  /** The order its listeners run in, pinned: Spring would otherwise follow their package paths. */
  object ListenerOrder {
    /** Each slice drops what a master loses into a new world, before the masters are re-homed. */
    const val CARRY_OVER = 5

    /** After [CARRY_OVER], so the items of every owner that is gone are swept up with the rest. */
    const val CARRY_OVER_ITEMS = 6
    const val MASTER_HOMES = 10
    const val KNOWLEDGE = 20
    const val MONEY_SUPPLY = 30
    const val RESERVE = 40
    const val PLACE_REGIONS = 50
    const val PROVINCES = 60
  }
}