package net.bestia.zone.item.script

import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class AppleScript(
  private val messageProcessor: OutMessageProcessor
) : ItemScript {
  override val itemId = 1L

  override fun execute(world: World, userId: EntityId): Boolean {
    val hpComp = world.get(userId, Health::class)
      ?: return false

    val healAmount = 25

    hpComp.current += healAmount

    val pos = world.get(userId, Position::class)

    if (pos != null) {
      val healMsg = DamageEntitySMSG.fromItemHeal(userId, healAmount)
      messageProcessor.sendToAllPlayersInRange(pos.toVec3L(), healMsg)
    }

    return true
  }
}
