package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 208 (`TELEPORT_SICKNESS`): five minutes after a Sage's Teleport, during which the body
 * cannot teleport again. Unlike [TeleportLock], which only stops wings being chained, this is the skill's own rule.
 */
@Component
class TeleportSickness : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override fun durationSeconds(level: Int): Double = SICK_SECONDS

  private companion object {
    const val SICK_SECONDS = 300.0
  }
}
