package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `TRAP_WARY`: a wild bestia broke out of a trap and steps around the next one for a while, see
 * [net.bestia.zone.capture.BestiaTrapSystem]. Without it, three traps in a row would give three rolls in three ticks.
 */
@Component
class TrapWary : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double = 30.0
}
