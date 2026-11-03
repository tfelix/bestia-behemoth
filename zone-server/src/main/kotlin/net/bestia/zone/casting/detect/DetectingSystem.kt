package net.bestia.zone.casting.detect

import net.bestia.zone.aoi.AoiLayer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component as SpringComponent

/** Refreshes what every [Detecting] bearer senses around it, and ends it when its time runs out. */
@SpringComponent
class DetectingSystem(
  private val aoi: EntityAOIService,
) : System {
  override val phase = Phase.STATUS
  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val reads: ComponentClassSet = setOf(Position::class, Master::class, OwnedBestia::class)
  override val writes: ComponentClassSet = setOf(Detecting::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Detecting::class, Position::class).each { id ->
      val detecting = get<Detecting>()
      detecting.remainingSeconds -= deltaTime
      if (detecting.remainingSeconds <= 0f) {
        world.remove(id, Detecting::class)
        return@each
      }

      detecting.update(sense(world, id, get<Position>().toVec3L()))
    }
  }

  private fun sense(world: World, bearerId: EntityId, at: Vec3L): List<Detecting.Detected> {
    val found = mutableListOf<Detecting.Detected>()
    aoi.forEachInCube(at, RADIUS_TILES * 2, AoiLayer.DYNAMIC_ONLY) { id, x, y, z ->
      val isMaster = world.has(id, Master::class)
      if (id != bearerId && (isMaster || world.has(id, OwnedBestia::class))) {
        found.add(Detecting.Detected(id, Vec3L(x, y, z), isMaster))
      }
    }

    // In a fixed order, so the same bodies standing still do not count as news.
    return found.sortedBy { it.entityId }
  }

  private companion object {
    const val RADIUS_TILES = 15L
  }
}
