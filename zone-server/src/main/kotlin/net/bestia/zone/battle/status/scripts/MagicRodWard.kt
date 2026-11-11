package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 206 (`MAGIC_ROD`): holds for 10 s, 5 s more per level, until it catches a spell. The
 * catching itself is `MagicRodCatch`, at the moment a cast resolves.
 */
@Component
class MagicRodWard : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double = BASE_SECONDS + SECONDS_PER_LEVEL * (level - 1)

  private companion object {
    const val BASE_SECONDS = 10.0
    const val SECONDS_PER_LEVEL = 5.0
  }
}
