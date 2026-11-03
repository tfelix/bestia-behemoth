package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * Runs from whoever caused it, see `FearSystem`. No attacks and no skills on the run, but a potion still
 * goes down, so a frightened player is not helpless.
 */
@Component
class Feared(private val castCancelService: CastCancelService) : StatusEffectScript {

  override val actionLocks: Set<ActionLock> = setOf(ActionLock.MOVE, ActionLock.ATTACK, ActionLock.CAST)

  override fun durationSeconds(level: Int): Double {
    return 3.0
  }

  override fun onApply(context: StatusEffectHookContext) {
    castCancelService.interrupt(context.world, context.hostId)
  }
}
