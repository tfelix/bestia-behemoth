package net.bestia.zone.ecs.spawn

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.BestiaEntitySpawner
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import org.springframework.core.annotation.Order
import kotlin.random.Random
import org.springframework.stereotype.Component as SpringComponent

/**
 * Keeps every **awake** den stocked, and lets the rest sleep.
 *
 * ### Dormancy is the whole point
 *
 * `worldgen`'s spawner stage puts on the order of a thousand dens on the 128 km world and some ten thousand on
 * a 512 km one. A pack of six each would be tens of thousands of entities, nearly all of them out of anybody's
 * sight. So a den does nothing until a player is inside its [Spawner.activationRange], and when the last one
 * leaves, its pack is taken back out of the world - `spawnedEntities` is emptied so the den restocks fresh
 * next time rather than resuming half full with stale positions.
 *
 * ### Two bugs this file used to have
 *
 * Both were the kind that look like working code. `spawnMissingEntities` called
 * `spawnMob(world, "blob", ...)` with a literal, so **every den in the world produced blobs** whatever
 * its `bestiaId` said - which would have made the entire level ramp invisible while the spawn system appeared
 * to work perfectly. And it spawned at `z = 0`, sea level, which for a den on a hillside is underground.
 *
 * The height fix is deliberately **not** a call to `ChunkService` from here.
 * `ChunkStreamSystem.groundNewcomers` already snaps anything without a `Grounded` marker onto the surface on
 * the tick after it appears, and two mechanisms for "put this entity on the ground" is one too many. What this
 * passes instead is the **den's own z**, which `WildSpawnerService` took from the terrain when it placed the
 * den - so a creature starts within a voxel or two of its final height and is exact one tick later.
 */
@SpringComponent
@Order(80)
class SpawnerSystem(
  private val bestiaEntitySpawner: BestiaEntitySpawner,
) : System {

  override val reads: ComponentClassSet = setOf(ActivePlayer::class)
  override val writes: ComponentClassSet = setOf(Spawner::class)

  override fun update(world: World, deltaTime: Float) {
    // Collected once per tick rather than queried per den. With a thousand dens and a handful of players, the
    // other order asks the same question a thousand times.
    val players = activePlayerPositions(world)

    world.query(Spawner::class).each {
      val spawner = get<Spawner>()
      removeDeadEntities(spawner, world)

      val wasAwake = spawner.awake
      spawner.awake = players.any { player -> withinActivation(spawner, player) }

      when {
        spawner.awake -> spawnMissingEntities(spawner, world)
        // Only on the transition. After the first quiet tick the set is already empty, and walking it again
        // for every dormant den on the world is exactly the cost dormancy exists to avoid.
        wasAwake -> despawnPack(spawner, world)
      }
    }
  }

  /**
   * Where the players who have actually picked a master are standing.
   *
   * The same set `ChunkStreamSystem` anchors chunk streaming on, and queried the same way: an account that has
   * not chosen one is not standing anywhere, so it must not wake a den.
   */
  private fun activePlayerPositions(world: World): List<Vec3L> {
    val positions = ArrayList<Vec3L>()
    world.query(Position::class, ActivePlayer::class).each {
      positions.add(get<Position>().toVec3L())
    }
    return positions
  }

  /**
   * Horizontal distance only, and squared so nothing takes a root.
   *
   * Horizontal because a den and a player on the same hillside can be a hundred metres apart vertically while
   * in plain sight of one another, and a gallery forty metres down is not in sight of the surface either.
   * Height is the wrong axis for "can this be seen"; the right answer to that is line of sight, which is not
   * this system's business.
   */
  private fun withinActivation(spawner: Spawner, player: Vec3L): Boolean {
    val dx = player.x - spawner.position.x
    val dy = player.y - spawner.position.y
    val reach = spawner.activationRange.toLong()
    return dx * dx + dy * dy <= reach * reach
  }

  private fun spawnMissingEntities(spawner: Spawner, world: World) {
    if (spawner.spawnedEntities.size >= spawner.maxSpawnCount) {
      return
    }

    val x = randomBetween(spawner.position.x - spawner.range / 2, spawner.position.x + spawner.range / 2)
    val y = randomBetween(spawner.position.y - spawner.range / 2, spawner.position.y + spawner.range / 2)

    // `spawner.bestiaId`, not a literal, and the den's own z, not zero. See the class KDoc.
    val spawnedEntityId = bestiaEntitySpawner.spawnMob(
      world,
      spawner.bestiaId,
      Vec3L(x, y, spawner.position.z)
    )

    spawner.spawnedEntities.add(spawnedEntityId)
  }

  /** Takes the pack back out of the world, so the den restocks fresh rather than resuming half full. */
  private fun despawnPack(spawner: Spawner, world: World) {
    if (spawner.spawnedEntities.isEmpty()) return

    LOG.debug { "Den at ${spawner.position} went dormant; despawning ${spawner.spawnedEntities.size}" }
    for (entityId in spawner.spawnedEntities) {
      if (world.hasEntity(entityId)) world.destroy(entityId)
    }
    spawner.spawnedEntities.clear()
  }

  private fun removeDeadEntities(spawner: Spawner, world: World) {
    spawner.spawnedEntities.removeIf { entityId -> !world.hasEntity(entityId) }
  }

  fun randomBetween(x: Long, y: Long): Long {
    return Random.nextLong(x, y + 1)
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
