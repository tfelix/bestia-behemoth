package net.bestia.zone.inn

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PropFootprint
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component

/** The inns players built in their towns. Each is open while its town's sign stands, and saves at its own spot. */
@Component
class PlayerInns(
  private val towns: TownRegistry,
  private val worldService: WorldService,
) {

  fun isInReach(world: World, actorId: EntityId, inn: EntityId): Boolean {
    val visual = world.get(inn, StaticVisual::class) ?: return false
    val pose = world.get(inn, PropPose::class) ?: return false
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return false

    return PropFootprint.distanceTo(actorAt, pose, visual, worldService.config.voxelSize) <= REACH_TILES
  }

  /** Null while the inn stands in no town. */
  fun counterOf(world: World, inn: EntityId): InnCounter? {
    val position = world.get(inn, PropPose::class)?.position ?: return null
    val town = towns.standingAt(position) ?: return null

    return InnCounter(inn, position, town.name)
  }

  private companion object {
    /** As at a post office: the client stops two tiles out, and one more absorbs latency. */
    const val REACH_TILES = 3.0
  }
}
