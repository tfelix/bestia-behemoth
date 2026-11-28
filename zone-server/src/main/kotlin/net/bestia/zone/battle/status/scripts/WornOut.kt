package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.status.StaminaConfig
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `WORN_OUT`: worked a short while ago. While it lasts, stamina comes back only from food, so a worker cannot
 * work, rest a moment and work on without eating. Every unit of work starts it anew. Read by
 * [net.bestia.zone.battle.ecs.status.StaminaRegenSystem].
 */
@Component
class WornOut(private val config: StaminaConfig) : StatusEffectScript {

  override val isCancellable: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return config.wornOutSeconds
  }
}
