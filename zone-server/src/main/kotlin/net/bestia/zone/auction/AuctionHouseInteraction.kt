package net.bestia.zone.auction

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PropFootprint
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.StartTownMarkets
import org.springframework.stereotype.Component

/** Clicking an auction house is how its window opens. The building is the entry point, not a clerk. */
@Component
class AuctionHouseInteraction(
  private val worldService: WorldService,
  private val startTownMarkets: StartTownMarkets,
  private val outMessageProcessor: OutMessageProcessor,
) : PropInteraction {

  override val kind = StaticEntityKind.AUCTION_HOUSE

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val actorAt = world.get(actorId, Position::class)?.toVec3L() ?: return OpError.AUCTION_OUT_OF_RANGE
    val pose = world.get(propId, PropPose::class) ?: return OpError.AUCTION_OUT_OF_RANGE
    val visual = world.get(propId, StaticVisual::class) ?: return OpError.AUCTION_OUT_OF_RANGE

    if (PropFootprint.distanceTo(actorAt, pose, visual, worldService.config.voxelSize) > REACH_TILES) {
      return OpError.AUCTION_OUT_OF_RANGE
    }

    val hallPropId = world.get(propId, WorldObjectIdentity::class)?.propId ?: return null
    val town = startTownMarkets.townOf(hallPropId) ?: return null
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    outMessageProcessor.sendToPlayer(accountId, AuctionHouseOpenSMSG(propId, town, startTownMarkets.towns()))

    return null
  }

  companion object {
    /** Tiles from the walls. The client stops two tiles out, so one tile of slack absorbs latency. */
    const val REACH_TILES = 3.0
  }
}
