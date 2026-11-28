package net.bestia.zone.faction.ecs

import net.bestia.worldgen.core.Faction
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.DirtyFlag
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Removable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * The Order a master is sworn to, shown on its name tag to everyone nearby. On the master and its bestias, so a
 * rival's bestia can be told apart too; [FactionBadgeSystem] keeps it in step with the master's membership.
 */
data class FactionBadge(val faction: Faction) : Component, Removable {
  override val dirtyFlag = DirtyFlag()

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return FactionBadgeComponentSMSG(entityId, faction, removed)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }
}
