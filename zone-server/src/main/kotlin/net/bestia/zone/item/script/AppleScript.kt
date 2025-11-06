package net.bestia.zone.item.script

import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.entity.DamageEntitySMSG
import net.bestia.zone.message.processor.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class AppleScript(
  private val messageProcessor: OutMessageProcessor
) : ItemScript {
  override val itemId = 1L

  override fun execute(user: Entity): Boolean {
    val hpComp = user.get(Health::class)
      ?: return false

    val healAmount = 25

    hpComp.current += healAmount

    val pos = user.get(Position::class)

    if (pos != null) {
      val healMsg = DamageEntitySMSG.fromItemHeal(user.id, healAmount)
      messageProcessor.sendToAllPlayersInRange(pos.toVec3L(), healMsg)
    }

    return true
  }
}
