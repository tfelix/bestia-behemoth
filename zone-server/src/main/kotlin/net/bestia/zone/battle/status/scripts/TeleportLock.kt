package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 16 (`TELEPORT_LOCK`) - bookkeeping, never shown, no stat effect.
 *
 * Put on a body by a fly wing or a butterfly wing, which refuse while it lasts. Short on purpose: it stops a
 * player from chaining wings faster than the eye can follow, not from using them. No status value shortens
 * cooldowns yet, so it is a flat time.
 */
@Component
class TeleportLock : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override fun durationSeconds(level: Int): Double = LOCK_SECONDS

  private companion object {
    const val LOCK_SECONDS = 3.5
  }
}
