package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.Healing
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import org.springframework.stereotype.Component

/** Heals a little every few seconds. A heal like any other, so sickness halves it. */
@Component
class Regenerating : StatusEffectScript {

  override val tickIntervalSeconds: Float = 2f

  override fun durationSeconds(level: Int): Double {
    return 10.0
  }

  override fun onTick(context: StatusEffectTickContext) {
    val max = context.world.get(context.hostId, Health::class)?.max ?: return

    Healing.heal(context.world, context.hostId, (max * HEAL_PERCENT / 100).coerceAtLeast(1))
  }

  private companion object {
    const val HEAL_PERCENT = 3
  }
}
