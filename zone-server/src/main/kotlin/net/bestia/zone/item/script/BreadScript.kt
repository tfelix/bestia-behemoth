package net.bestia.zone.item.script

import net.bestia.zone.ecs.battle.status.Stamina
import net.bestia.zone.ecs.core.World
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Stamina only, where [AppleScript] heals and [HeartyStewScript] does both.
 *
 * That is what makes the cheapest food in the game worth carrying without making it a potion: a loaf keeps
 * you walking, it does not close a wound.
 */
@Component
class BreadScript : ItemScript {
  override val itemId = 30L

  override fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean {
    val stamina = world.get(userId, Stamina::class) ?: return false

    // CurMax.current clamps itself, so eating when full simply wastes the loaf.
    stamina.current += STAMINA_RESTORED

    return true
  }

  private companion object {
    const val STAMINA_RESTORED = 35
  }
}
