package net.bestia.zone.item.script

import net.bestia.zone.battle.damage.DamageEntitySMSG
import net.bestia.zone.ecs.battle.status.Health
import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * What Cooking produces: the only consumable that restores stamina as well as health.
 *
 * Succeeds on the stamina alone when the eater has no [Health] - the reverse of [AppleScript], which
 * refuses outright - because a cooked meal that a healthy Master cannot eat would make Cooking useless
 * to the one player most likely to have taken it.
 */
@Component
class HeartyStewScript(
  private val messageProcessor: OutMessageProcessor
) : ItemScript {
  override val itemId = 18L

  override fun execute(world: World, userId: EntityId): Boolean {
    val health = world.get(userId, Health::class)
    val stamina = world.get(userId, Stamina::class)

    if (health == null && stamina == null) {
      return false
    }

    // CurMax.current clamps to [0, max] itself, so an already-full eater simply wastes the meal.
    health?.let { it.current += HEALTH_RESTORED }
    stamina?.let { it.current += STAMINA_RESTORED }

    if (health != null) {
      world.get(userId, Position::class)?.let { position ->
        messageProcessor.sendToAllPlayersInRange(
          position.toVec3L(),
          DamageEntitySMSG.fromItemHeal(userId, HEALTH_RESTORED)
        )
      }
    }

    return true
  }

  private companion object {
    const val HEALTH_RESTORED = 60
    const val STAMINA_RESTORED = 40
  }
}
