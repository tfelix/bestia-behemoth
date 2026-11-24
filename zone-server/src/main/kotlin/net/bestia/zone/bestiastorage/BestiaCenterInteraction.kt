package net.bestia.zone.bestiastorage

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PropFootprint
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component

/** Clicking a player-built storage center works like its keeper's counter, while its town stands. */
@Component
class BestiaCenterInteraction(
  private val storage: BestiaStorageService,
  private val towns: TownRegistry,
  private val worldService: WorldService,
) : PropInteraction {

  override val kind = StaticEntityKind.BESTIA_CENTER

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val visual = world.get(propId, StaticVisual::class) ?: return null
    val pose = world.get(propId, PropPose::class) ?: return null
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return null
    if (PropFootprint.distanceTo(actorAt, pose, visual, worldService.config.voxelSize) > REACH_TILES) {
      return OpError.TALK_OUT_OF_RANGE
    }
    if (towns.standingAt(pose.position) == null) {
      return OpError.BESTIA_STORAGE_CENTER_CLOSED
    }
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    storage.openAtCounter(accountId)

    return null
  }

  private companion object {
    /** As at a post office: the client stops two tiles out, and one more absorbs latency. */
    const val REACH_TILES = 3.0
  }
}
