package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * An entity's current, effective status values - [BaseStatusValues] with every learned passive, worn item
 * and active status effect applied. Written only by
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
  override val dirtyFlag = DirtyFlag()

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
