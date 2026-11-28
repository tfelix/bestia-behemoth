package net.bestia.zone.faction.banishment

import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** `status_effects.yml` id 312 (`UNSWORN`): halves every attribute. [UnswornMark] keeps it on. */
@Component
class Unsworn : StatusEffectScript {

  /** Derived from the stored membership on every spawn, so a stored copy would only be stale. */
  override val isPersisted: Boolean = false

  override val isCancellable: Boolean = false

  /** Only a fallback: [UnswornMark] always passes the time that is really left. */
  override fun durationSeconds(level: Int): Double {
    return FALLBACK_DURATION_SECONDS
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.strength /= 2
    context.vitality /= 2
    context.intelligence /= 2
    context.dexterity /= 2
    context.willpower /= 2
    context.agility /= 2
  }

  private companion object {
    const val FALLBACK_DURATION_SECONDS = 60.0
  }
}
