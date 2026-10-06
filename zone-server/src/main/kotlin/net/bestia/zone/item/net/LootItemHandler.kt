package net.bestia.zone.item.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/**
 * Attaches a [ObtainItemIntent.LootItemIntent] to the player's current active entity; the actual
 * loot resolution (range/capacity checks, granting the item) happens in
 * [net.bestia.zone.item.ecs.ObtainItemIntentSystem] on the next tick.
 */
@Component
class LootItemHandler(
  private val connectionInfoService: ConnectionInfoService,
) : TickMessageHandler<LootItemCMSG> {
  override val wire = decoder(MessageCase.LOOT_ITEM) { accountId, envelope ->
    LootItemCMSG.fromBnet(accountId, envelope.lootItem)
  }

  override fun handle(world: World, msg: LootItemCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    world.modify(activeEntityId) { id ->
      add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = msg.targetEntityId))
    }

    return true
  }
}
