package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** Left by Suppress Magic: no casting, and whatever was being cast is lost. Moving and swinging still work. */
@Component
class Silenced(private val castCancelService: CastCancelService) : StatusEffectScript {

  override val actionLocks: Set<ActionLock> = setOf(ActionLock.CAST)

  /** Suppress Magic's table: 3 s at Lv. 1, one more per level. */
  override fun durationSeconds(level: Int): Double {
    return 2.0 + level
  }

  override fun onApply(context: StatusEffectHookContext) {
    castCancelService.interrupt(context.world, context.hostId)
  }
}
