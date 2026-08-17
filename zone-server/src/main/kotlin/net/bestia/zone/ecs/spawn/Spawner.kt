package net.bestia.zone.ecs.spawn

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * A den: somewhere creatures come from, and the bookkeeping that keeps the right number of them alive.
 *
 * Most dens on a world are **dormant**, and that is the design rather than an optimisation. `worldgen`'s
 * spawner stage puts some thirty thousand of these on a 128 km world; if every one kept a pack of ten alive,
 * the server would carry a third of a million entities nobody is looking at. [activationRange] is what keeps
 * that from happening - see [SpawnerSystem].
 */
class Spawner(
  /**
   * This den's durable name, so a creature it made can find its way back after a restart.
   *
   * Required, with no default, deliberately. There are only two places a `Spawner` is built, and a
   * placeholder identity would not fail here - it would produce creatures that persist a membership
   * pointing at nothing, and surface a restart later as a pack that quietly doubled.
   */
  val identity: DenIdentity,

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
    // The broad phase looks at the 3x3 cells around a player, so a range reaching past one cell would be
    // honoured near the den and silently ignored at the far edge of the same radius - a den that half works.
    require(activationRange <= SpawnerSystem.MAX_ACTIVATION_RANGE) {
      "activationRange $activationRange exceeds SpawnerSystem.MAX_ACTIVATION_RANGE " +
          "${SpawnerSystem.MAX_ACTIVATION_RANGE}, which is what SpawnerCellIndex's cells are sized against; " +
          "this den would not be found at the outer edge of its own range"
    }
  }

  var spawnedEntities: MutableSet<EntityId> = mutableSetOf()

  companion object {
    /** Roughly three chunks beyond a typical view radius, in world units. */
    const val DEFAULT_ACTIVATION_RANGE = 220
  }
}
