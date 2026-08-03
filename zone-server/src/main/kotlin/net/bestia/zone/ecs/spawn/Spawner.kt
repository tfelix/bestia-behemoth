package net.bestia.zone.ecs.spawn

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * A den: somewhere creatures come from, and the bookkeeping that keeps the right number of them alive.
 *
 * Most dens on a world are **dormant**, and that is the design rather than an optimisation. `worldgen`'s
 * spawner stage puts on the order of a thousand of these on a 128 km world; if every one kept a pack of six
 * alive, the server would carry several thousand entities nobody is looking at. [activationRange] is what
 * keeps that from happening - see [SpawnerSystem].
 */
class Spawner(
  /** Which species. This was ignored once; see [SpawnerSystem.spawnMissingEntities]. */
  val bestiaId: Long,
  val maxSpawnCount: Int = 1,
  val position: Vec3L,

  /** Edge of the box, in world units, that spawned creatures are placed inside. */
  val range: Int,

  /**
   * How near a player must be, in world units, before this den does anything at all.
   *
   * Beyond a player's own view radius by default, so creatures are already standing where they belong by the
   * time anybody can see them - a den that woke at the edge of vision would pop its pack into existence in
   * front of the player.
   */
  val activationRange: Int = DEFAULT_ACTIVATION_RANGE,
) : Component {

  init {
    require(range >= 1) { "Range must be >= 1" }
    require(activationRange >= range) {
      "activationRange $activationRange must be at least the spawn range $range, or a den could place a " +
          "creature further away than the distance that woke it"
    }
  }

  var spawnedEntities: MutableSet<EntityId> = mutableSetOf()

  /**
   * Whether a player was near enough on the last tick that looked.
   *
   * Kept on the component rather than recomputed at each use, because the *transition* is what matters: a den
   * going quiet is what takes its pack back out of the world, and "it was awake and now is not" cannot be
   * read off the distance alone.
   */
  var awake: Boolean = false

  companion object {
    /** Roughly three chunks beyond a typical view radius, in world units. */
    const val DEFAULT_ACTIVATION_RANGE = 220
  }
}
