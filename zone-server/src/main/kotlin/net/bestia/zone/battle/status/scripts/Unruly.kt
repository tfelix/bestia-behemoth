package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * Registered under `status_effects.yml` id 255 (`UNRULY`): a bestia too far above its master's level ignores them
 * for a minute. [net.bestia.zone.battle.UnrulyGuard] sets it off and refuses the commands; the bestia's own AI
 * drives it meanwhile, see [net.bestia.zone.ai.ecs.PlayerHold].
 *
 * No action locks: those would stop its own AI from moving and fighting too.
 */
@Component
class Unruly : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double = 60.0
}
