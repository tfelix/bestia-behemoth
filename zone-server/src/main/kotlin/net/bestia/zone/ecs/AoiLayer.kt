package net.bestia.zone.ecs

import java.util.EnumSet

/**
 * Which population an [AreaOfInterestService] entry belongs to, so a query can ask for one of them.
 *
 * The index holds two kinds of thing that differ by three or four orders of magnitude in count, and
 * almost every question is about one kind or the other:
 *
 * - **"who is near me"** - perception, the initial entity snapshot a client asks for on login. These
 *   want [DYNAMIC] only. A mob standing in a dense wood is surrounded by hundreds of trees and
 *   interested in none of them, and the snapshot must not re-send statics the chunk batch already
 *   delivered.
 * - **"what is in this volume"** - an area-of-effect skill, a placement check. These want everything,
 *   because a fireball that spares the trees is the defect.
 *
 * [ALL] is the default on purpose. A new area query that forgets to say what it wants gets everything,
 * which is the answer that is merely slower rather than the answer that is wrong.
 */
enum class AoiLayer {

  /** Moves under its own power: players, mobs, dropped items. Positions change, often every tick. */
  DYNAMIC,

  /**
   * Placed once and then still: trees, crystals, and later player-built walls and structures.
   *
   * Entries in this layer are inserted when their chunk becomes resident and removed when it stops
   * being, and never move in between - so they never pay the re-home cost a moving entry does.
   */
  STATIC;

  companion object {
    val ALL: Set<AoiLayer> = EnumSet.allOf(AoiLayer::class.java)
    val DYNAMIC_ONLY: Set<AoiLayer> = EnumSet.of(DYNAMIC)
    val STATIC_ONLY: Set<AoiLayer> = EnumSet.of(STATIC)
  }
}
