package net.bestia.zone.item.loot

import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.message.TickMessageHandler
import org.springframework.stereotype.Component

/**
 * Attaches a [ObtainItemIntent.LootItemIntent] to the player's current active entity; the actual
 * loot resolution (range/capacity checks, granting the item) happens in
 * [net.bestia.zone.ecs.item.ObtainItemIntentSystem] on the next tick.
 */
@Component
class LootItemHandler(
  private val connectionInfoService: ConnectionInfoService,
) : TickMessageHandler<LootItemCMSG> {
  override val handles = LootItemCMSG::class

  override fun handle(world: World, msg: LootItemCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    world.modify(activeEntityId) { id ->
      add(id, ObtainItemIntent.LootItemIntent(sourceEntityItemStackId = msg.targetEntityId))
    }

    return true
  }
}
