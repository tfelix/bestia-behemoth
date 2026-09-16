package net.bestia.zone.ecs.spawn.townsfolk

import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.DirtyableComponent
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * What one inhabitant of a settlement looks like, and what they are called.
 *
 * Takes the place of `EntityVisual` on a townsperson rather than joining it. They share the commoner
 * archetype's behaviour, but appearance and name belong to the individual, and a catalogue id can only
 * say "a townsperson" - which is what every one of them was labelled before this existed.
 *
 * [name] rides here rather than on a component of its own for `MasterVisual`'s reason: it is public, it
 * does not change while the entity lives, and every viewer needs it exactly when the body arrives.
 *
 * Neither field has a setter. A person is rebuilt from their identity whenever somebody comes near, so
 * there is nothing to mutate - a change of appearance is a differently-built entity.
 */
data class TownsfolkVisual(
  val name: String,
  val body: TownsfolkBody
) : DirtyableComponent() {

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return TownsfolkVisualComponentSMSG(entityId, name, body)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }
}
