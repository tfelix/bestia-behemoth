package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * The unbuffed status values for an entity - untouched by status effects, but permanently raised
 * by investing a [StatusPoints] point. [StatusValues] is the effective, current counterpart
 * recomputed from this by `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem`, the same
 * base/effective split [net.bestia.zone.ecs.movement.Speed] already uses for `baseSpeed`/`speed`.
 *
 * Synced to the owner alongside [StatusValues] because the next status point is priced off *this*
 * value, not the effective one - see
 * [net.bestia.zone.account.master.status.EffortValueCostCalculator]. Pricing off the effective value
 * would make a point cost more for as long as a buff happened to be running.
 */
data class BaseStatusValues(
  var strength: Int,
  var intelligence: Int,
  var vitality: Int,
  var dexterity: Int,
  var willpower: Int,
  var agility: Int
) : Component, Dirtyable {

  private var dirty: Boolean = true

  override fun isDirty(): Boolean = dirty

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

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
}
