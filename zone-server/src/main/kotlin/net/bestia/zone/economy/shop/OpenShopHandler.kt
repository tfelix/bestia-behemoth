package net.bestia.zone.economy.shop

import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/**
 * Answers with what one merchant has, at the prices of wherever the sender is standing.
 *
 * Unlike [ShopTradeHandler] this needs no intent, because it changes nothing: opening a window is a
 * read, and a read cannot create a settlement's ledger row - see `SettlementEconomyService`. The world
 * lock is taken for the position and the catch-up, which is what a `WorldView.read` gives.
 *
 * The merchant is re-checked for distance rather than trusted. A conversation has already established
 * they are in earshot, but a hand-made packet has had no conversation.
 */
@Component
class OpenShopHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val economy: SettlementEconomyService,
  private val merchants: MerchantStock,
  private val offers: ShopOfferPublisher,
  private val outMessageProcessor: OutMessageProcessor,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<OpenShopCMSG> {
  override val handles = OpenShopCMSG::class

  override fun handle(msg: OpenShopCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Outside the read below rather than inside it: resolving a townsperson takes the world lock itself.
    val stocked = merchants.of(msg.merchantEntityId)

    val shop = if (stocked == null) {
      null
    } else {
      world.read {
        val position = get(activeEntityId, Position::class)?.toVec3L()
        val counter = get(msg.merchantEntityId, Position::class)?.toVec3L()

        if (position == null || counter == null || !withinReach(position, counter)) {
          null
        } else {
          economy.shopAt(position.x, position.y)
        }
      }
    }

    if (shop == null || stocked == null) {
      outMessageProcessor.sendToPlayer(
        msg.playerId,
        OperationErrorSMSG(OperationErrorProto.OpError.SHOP_NONE_HERE)
      )
      return true
    }

    offers.publishTo(msg.playerId, msg.merchantEntityId, shop.first, shop.second, stocked)

    return true
  }

  private fun withinReach(player: Vec3L, merchant: Vec3L): Boolean {
    return player.distance(merchant) <= MAX_SHOP_RANGE
  }

  private companion object {
    /**
     * `TalkService.MAX_TALK_RANGE`, and not shared with it for that constant's own reason: the two are
     * allowed to diverge, and a counter is something you stand at rather than shout across.
     */
    const val MAX_SHOP_RANGE = 10L
  }
}
