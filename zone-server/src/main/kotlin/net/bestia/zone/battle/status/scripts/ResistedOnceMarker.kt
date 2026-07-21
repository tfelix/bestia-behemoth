package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * Registered under `status_effects.yml` id 4 (`RESISTED_ONCE_MARKER`) - internal bookkeeping only,
 * tracks that this entity already resisted a debuff once this encounter. Never shown to the client
 * ([net.bestia.zone.battle.status.StatusEffectDefinition.isSyncedToClient] is false) and has
 * nothing to apply, just a duration and a "don't stack" rule.
 */
@Component
class ResistedOnceMarker : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override fun durationSeconds(level: Int): Double = 5.0
}
