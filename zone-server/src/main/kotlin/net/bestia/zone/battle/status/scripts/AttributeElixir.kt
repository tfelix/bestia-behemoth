package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * An Alchemist's elixir: one attribute raised by [BONUS_PER_LEVEL] a level for ten minutes. The level is the elixir's
 * tier - plain, greater, and the Philosopher's Draught that holds all six at once.
 *
 * One status effect per attribute, so the six stack with each other; a stronger elixir of one attribute replaces a
 * weaker one and a weaker one leaves a stronger in place.
 */
abstract class AttributeElixir : StatusEffectScript {

  override val stackBehavior: StackBehavior get() = StackBehavior.REPLACE_IF_STRONGER

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    raise(context, BONUS_PER_LEVEL * level)
  }

  protected abstract fun raise(context: StatusValueRecalcContext, bonus: Int)

  companion object {
    const val DURATION_SECONDS = 600.0
    const val BONUS_PER_LEVEL = 5
  }
}
