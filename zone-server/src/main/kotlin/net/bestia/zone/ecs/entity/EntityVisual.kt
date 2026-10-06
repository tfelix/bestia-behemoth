package net.bestia.zone.ecs.entity

import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * What an entity looks like: a [VisualKind] plus an id into the matching client catalogue.
 *
 * One component for every kind of visual, so a new one costs a catalogue entry on the client and no
 * wire change. `MasterVisual` stays separate because a master's appearance is a set of parameters
 * rather than a single id.
 *
 * `Dirtyable`, unlike the static entities' `StaticVisual`: a mob or a spell effect arrives one entity
 * at a time rather than in the per-chunk batch that carries the ground it stands on.
 */
data class EntityVisual(
  val kind: VisualKind,
  val id: Long
) : Component, Dirtyable {
  override val dirtyFlag = DirtyFlag()

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return VisualComponentSMSG(entityId, kind, id)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
