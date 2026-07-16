package net.bestia.zone.item.loot

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Attaches a [ObtainItemIntent.LootItemIntent] to the player's current active entity; the actual
 * loot resolution (range/capacity checks, granting the item) happens in
 * [net.bestia.zone.ecs.item.ObtainItemIntentSystem] on the next tick.
 */
@Component
class LootItemHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<LootItemCMSG> {
  override val handles = LootItemCMSG::class

  override fun handle(msg: LootItemCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    world.modify(activeEntityId) { id ->
      add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = msg.targetEntityId))
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
