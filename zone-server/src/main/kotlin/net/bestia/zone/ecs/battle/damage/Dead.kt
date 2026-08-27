package net.bestia.zone.ecs.battle.damage

import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.DirtyableComponent
import net.bestia.zone.ecs.core.Removable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Entities with this tag are considered dead and their death logic will be executed.
 * This means:
 * - Loot is spawned
 * - EXP distributed
 *
 * For anything without an owner that is the whole story: [DeathSystem] destroys it the same tick, and
 * the client hears about it as a `VanishEntitySMSG` of kind `DEATH`. A player-owned entity instead
 * keeps this component and stays lying where it fell until it respawns, which is why the tag carries
 * state and is synced: it is the body's visible condition, not a one-off event. [Removable], so
 * respawning - taking it off - re-sends the same message with `removed = true`.
 *
 * A class rather than the `data object` it used to be, because the dirty flag and [resolved] are
 * per-entity and a shared singleton instance would have every corpse writing over the same fields.
 */
class Dead : DirtyableComponent(), Removable {

  /**
   * Whether [PlayerDeathSystem] has already charged this death's cost. The component outlives the
   * tick it was added on, so without this the EXP penalty would be applied again every tick the body
   * lies there.
   */
  var resolved: Boolean = false

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return DeadComponentSMSG(entityId = entityId, removed = removed)
  }

  // A corpse is as visible as the living entity was, so everyone nearby needs to see it.
  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }
}
