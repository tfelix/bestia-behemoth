package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 9 (`INVULNERABLE`): nothing may harm the bearer. The catalogue's `shield: ALL` is
 * what the damage gate reads; this script only says it lasts forever.
 *
 * Townsfolk carry it because they are non-combatant this release. A settlement's roster comes from the world
 * generator and nothing puts a person back, so a killable baker is a bakery that closes forever the first
 * time somebody idly swings at it.
 */
@Component
class InvulnerableEffect : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }
}
