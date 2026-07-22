package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * An entity's current, effective status values - [BaseStatusValues] with every active status
 * effect (and later, equipment) applied. Written only by
 * `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem`; everything else (regen systems,
 * [net.bestia.zone.battle.BattleContextFactory]) only ever reads it.
 */
data class StatusValues(
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
    return StatusValuesComponentSMSG(
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
