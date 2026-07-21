package net.bestia.zone.ecs

import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG

interface Dirtyable {
  fun isDirty(): Boolean

  /**
   * Forces this component to be considered dirty so the next flush re-sends it even though no
   * field changed. The sanctioned way to satisfy a "client re-requested its current state"
   * (e.g. [net.bestia.zone.item.inventory.GetInventoryHandler]) resync-on-demand; there is no
   * longer a `World.markChanged` for this. Mutating a component through its own setters already
   * marks it dirty, so this is only needed when nothing actually changed.
   */
  fun markDirty()

  fun clearDirty()


  /**
   * [removed] is true only for the one extra call [ZoneEngine] makes when this component
   * implements [Removable] and was just taken off an entity; every regular dirty-flush call
   * uses the default.
   */
  fun toEntityMessage(entityId: Long, removed: Boolean = false): EntitySMSG

  /**
   * Who should receive this change this tick, resolved fresh every flush so it can depend on
   * live state (party membership, ...) rather than a fixed per-type rule.
   */
  fun syncTargets(world: World, entityId: EntityId): SyncTargets
}
