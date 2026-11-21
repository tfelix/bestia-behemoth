package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 253 (`WATCHER_STRENGTH`): Strengthen Watchers on a guild's watcher, +10% attack and attack
 * speed per level. Lasts as long as the watcher; a higher level replaces a lower one.
 */
@Component
class WatcherStrength : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.REPLACE_IF_STRONGER

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(attack = PERCENT_PER_LEVEL * level, aspd = PERCENT_PER_LEVEL * level)
  }

  private companion object {
    const val PERCENT_PER_LEVEL = 10
  }
}
