package net.bestia.zone.ecs.core

/**
 * Spreads per-entity work over ticks: an entity's turn comes once every `period` ticks, on a tick chosen by a
 * hash of its id. Hashed rather than `id % period`, because snowflake ids minted a millisecond apart share
 * their low bits and would all land on the same few ticks.
 */
object TickBuckets {

  fun isDue(tick: Long, id: Long, period: Long): Boolean {
    if (period <= 1) return true

    return Math.floorMod(tick + mixed(id), period) == 0L
  }

  /** The first tick after [tick] on which [id] is due. */
  fun nextDue(tick: Long, id: Long, period: Long): Long {
    if (period <= 1) return tick + 1

    val ticksUntilDue = Math.floorMod(-(tick + mixed(id)), period)
    return tick + if (ticksUntilDue == 0L) period else ticksUntilDue
  }

  /** MurmurHash3's finaliser: every input bit reaches every output bit. */
  private fun mixed(id: Long): Long {
    var h = id
    h = (h xor (h ushr 33)) * -0xae502812aa7333L
    h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
    h = h xor (h ushr 33)
    return h and Long.MAX_VALUE
  }
}
