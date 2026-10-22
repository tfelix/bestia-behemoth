package net.bestia.zone.guild.skill

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 14 (`GUILD_REGENERATION`), from [GuildRegeneration]. */
@Component
class GuildRegenerationEffect : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 60.0
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    val percent = PERCENT_PER_LEVEL * level
    context.addHpRegen(percent = percent)
    context.addManaRegen(percent = percent)
    context.addStaminaRegen(percent = percent)
  }

  private companion object {
    const val PERCENT_PER_LEVEL = 50
  }
}
