package net.bestia.zone.auction

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Clicking an auction house is how its window opens. The building is the entry point, not a clerk. */
@Component
class AuctionHouseInteraction(
  private val houses: AuctionHouses,
  private val outMessageProcessor: OutMessageProcessor,
) : PropInteraction {

  override val kind = StaticEntityKind.AUCTION_HOUSE

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val town = houses.townInReach(world, actorId, propId) ?: return OpError.AUCTION_OUT_OF_RANGE
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    outMessageProcessor.sendToPlayer(accountId, AuctionHouseOpenSMSG(propId, town, houses.linkedTowns()))

    return null
  }
}
