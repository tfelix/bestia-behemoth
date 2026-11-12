package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.status.Taunted
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** Forced to fight whoever provoked it: lasts `3 s + 1 s/lv` of Provoke. See `PerceptionSystem` for the fight. */
@Component
class Provoked : StatusEffectScript {

  /** It names an entity, and entity ids do not outlive a relog. */
  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 3.0 + level.coerceIn(1, MAX_LEVEL)
  }

  override fun onApply(context: StatusEffectHookContext) {
    val provoker = context.effect.sourceEntityId ?: return

    context.world.add(context.hostId, Taunted(provoker))
  }

  override fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
    context.world.remove(context.hostId, Taunted::class)
  }

  private companion object {
    const val MAX_LEVEL = 5
  }
}
