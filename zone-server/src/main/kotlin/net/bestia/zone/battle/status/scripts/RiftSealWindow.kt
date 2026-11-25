package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 259 (`RIFT_SEAL_WINDOW`): a rift's hold on its gate in the 30 minutes after its anchor
 * fell. It carries the rift's Potency, given when it is put on; a Dispel that lifts it seals the rift. Below the
 * threshold a Dispel has no chance at all, so a ritual's charge is what it takes.
 */
@Component
class RiftSealWindow : StatusEffectScript {

  override val dispelThreshold: Int = THRESHOLD

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return WINDOW_SECONDS
  }

  companion object {
    const val THRESHOLD = 80
    const val WINDOW_SECONDS = 30 * 60.0
  }
}
