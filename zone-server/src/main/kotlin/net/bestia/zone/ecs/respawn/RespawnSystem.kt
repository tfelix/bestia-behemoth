package net.bestia.zone.ecs.respawn

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.damage.TakenDamage
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.InCombat
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.GroundHeight
import net.bestia.zone.ecs.movement.Grounded
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Revives entities tagged [Respawn] at their save point with a single hit point.
 *
 * ### Why the vertical is resolved here
 *
 * A save point is a stored `x`/`y`; its `z` is from whenever it was written and may not be the ground
 * any more. Putting a body down below the surface is not a loud failure - every surrounding chunk is
 * solid rock, meshes to no surface and renders as a black screen, which looks exactly like terrain
 * failing to load (see [Grounded]). So `z` is taken from [GroundHeight], the same lookup
 * `ChunkStreamSystem.applyTeleports` and `MoveSystem` use, which is what makes a respawn, a walk and
 * a GM teleport agree about where the ground is.
 *
 * Dropping [Grounded] and leaving it to that system's grounding sweep would be a tick late: structural
 * changes made inside `update` are deferred to the end of the tick, so the marker would still be there
 * when the sweep runs. It is dropped only as a fallback, for the case the lookup cannot answer at all.
 *
 * `@Order(44)`, before `ChunkStreamSystem` (@45), so the chunk manifest that follows describes where
 * the player now is rather than where they died - the same reasoning that puts the teleport there.
 * That teleport could not have been reused: it only moves entities carrying `ActivePlayer` and is
 * keyed one destination per account, so it cannot move an owned bestia.
 */
@SpringComponent
@Order(44)
class RespawnSystem(
  private val groundHeight: GroundHeight,
) : System {

  override val reads: ComponentClassSet = setOf(Respawn::class)

  override val writes: ComponentClassSet = setOf(
    Position::class, Grounded::class, Path::class, Health::class,
    Dead::class, TakenDamage::class, InCombat::class
  )

  override fun update(world: World, deltaTime: Float) {
    world.query(Respawn::class, Position::class).each { id ->
      val savePoint = get<Respawn>().position
      val position = get<Position>()

      position.x = savePoint.x
      position.y = savePoint.y
      position.fraction = 0f

      val z = groundHeight.standingZAt(savePoint)
      if (z != null) {
        position.z = z
      } else {
        // Off the grid, or a world that is not generated yet. Keep the stored height and let the
        // grounding sweep have another go at it once there is terrain to ask about.
        position.z = savePoint.z
        world.remove(id, Grounded::class)
      }

      // Whatever the body was walking towards when it fell is not where it is any more.
      world.remove(id, Path::class)

      world.get(id, Health::class)?.let { health -> health.current = 1 }

      world.remove(id, Dead::class)
      world.remove(id, TakenDamage::class)
      world.remove(id, InCombat::class)
      world.remove(id, Respawn::class)

      LOG.debug { "Respawned entity $id at (${position.x},${position.y},${position.z})" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
