package net.bestia.zone.world.prop.collect

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.prop.CollectPropIntent
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/**
 * Attaches a [CollectPropIntent] to the player's active entity and returns.
 *
 * Deliberately as empty as [net.bestia.zone.item.loot.LootItemHandler]. Every check - does the prop exist, is
 * its kind collectible, has someone else already claimed it, is the player close enough - happens in
 * `CollectPropIntentSystem` on the tick thread, because the claim it has to make is a write to a map that is
 * only safe there. See [CollectPropIntent]'s KDoc.
 *
 * The acting entity comes from [ConnectionInfoService.getActiveEntityId] and never from the message; the id
 * the client sends names the *prop*.
 */
@Component
class CollectPropHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val deadActionGuard: DeadActionGuard,
) : TickMessageHandler<CollectPropCMSG> {
  override val wire = decoder(MessageCase.COLLECT_PROP) { accountId, envelope ->
    CollectPropCMSG.fromBnet(accountId, envelope.collectProp)
  }

  override fun handle(world: World, msg: CollectPropCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(world, activeEntityId, "collect a prop")) {
      return true
    }

    world.modify(activeEntityId) { id ->
      add(id, CollectPropIntent(propEntityId = msg.targetEntityId))
    }

    return true
  }
}
