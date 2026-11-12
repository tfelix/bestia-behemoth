package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.mark.MarkedPrey
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.ecs.core.update
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 230 (`HUNTERS_MARK`), the prey of a Hunter's Mark for 30 s. Each hunter marks one prey at a
 * time, which [MarkedPrey] on the hunter names; several hunters may mark the same one.
 */
@Component
class HuntersMarked : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.STACK_INDEPENDENT

  /** It names an entity, and entity ids do not outlive a relog. */
  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun onApply(context: StatusEffectHookContext) {
    val hunter = context.effect.sourceEntityId ?: return

    context.world.update(hunter, default = { MarkedPrey() }) { it.preyId = context.hostId }
  }

  override fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
    val hunter = context.effect.sourceEntityId ?: return
    val marked = context.world.get(hunter, MarkedPrey::class) ?: return

    if (marked.preyId == context.hostId) {
      marked.preyId = null
    }
  }

  companion object {
    private const val MAX_LEVEL = 5
    const val DURATION_SECONDS = 30.0

    /** Hunter's Mark's table: `+3%/lv` critical rate against the prey. */
    fun critPercent(level: Int): Int {
      return 3 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
