package net.bestia.zone.battle.ecs.status

import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * The unbuffed status values for an entity - untouched by status effects, but permanently raised
 * by investing a [StatusPoints] point. [StatusValues] is the effective, current counterpart
 * recomputed from this by `net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem`, the same
 * base/effective split [net.bestia.zone.movement.ecs.Speed] already uses for `baseSpeed`/`speed`.
 *
 * Synced to the owner alongside [StatusValues] because the next status point is priced off *this*
 * value, not the effective one - see
 * [net.bestia.zone.master.status.EffortValueCostCalculator]. Pricing off the effective value
 * would make a point cost more for as long as a buff happened to be running.
 *
 * Only masters and owned bestias carry one: their attributes are invested and persisted. A mob's are its species',
 * kept in the shared [net.bestia.zone.battle.status.CombatValues.base] of its [CombatStats].
 */
data class BaseStatusValues(
  var strength: Int,
  var intelligence: Int,
  var vitality: Int,
  var dexterity: Int,
  var willpower: Int,
  var agility: Int
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return BaseStatusValuesComponentSMSG(
      entityId = entityId,
      strength = strength,
      intelligence = intelligence,
      vitality = vitality,
      dexterity = dexterity,
      willpower = willpower,
      agility = agility
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.OwnerOnly

  /** These attributes as the immutable value the recalc and [CombatStats] work with. */
  fun toAttributes(): net.bestia.zone.battle.status.StatusValues {
    return net.bestia.zone.battle.status.StatusValues(
      strength = strength,
      vitality = vitality,
      intelligence = intelligence,
      agility = agility,
      willpower = willpower,
      dexterity = dexterity
    )
  }
}
