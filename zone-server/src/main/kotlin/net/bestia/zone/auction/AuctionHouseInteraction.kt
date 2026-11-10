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
  private val playerHouses: PlayerHouseService,
  private val outMessageProcessor: OutMessageProcessor,
) : PropInteraction {

  override val kind = StaticEntityKind.AUCTION_HOUSE

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    val at = houses.houseInReach(world, actorId, propId) ?: return OpError.AUCTION_OUT_OF_RANGE
    val accountId = world.get(actorId, Account::class)?.accountId ?: return null

    when (at) {
      is AuctionHouses.At.StartTown ->
        outMessageProcessor.sendToPlayer(accountId, AuctionHouseOpenSMSG(propId, at.townName, houses.linkedTowns()))
      is AuctionHouses.At.Player -> {
        if (!at.isOpen) return OpError.AUCTION_HOUSE_CLOSED
        playerHouses.open(accountId, propId, at)
      }
    }

    return null
  }
}
