package net.bestia.zone.ecs.place

import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.DirtyableComponent
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * A name and a reach, on the entity that owns them.
 *
 * The other half of the place system from [Place], answering a different question about the same ground:
 * [Place] tells one player where *they* are, privately; this is the name *on the thing*, public to anyone
 * who can see it. A town gate labelled for every passer-by and a player's own "you are in Ashford" are
 * not the same fact and must not share a component - one is `PublicInRange`, the other `OwnerOnly`.
 *
 * A player-founded area is an entity because a player has to be able to find it, look at it and lose it.
 * [AreaNameRegistry] is what makes it *resolvable* - an entity alone would mean a spatial query as wide as
 * the largest area in the world every time somebody moved.
 *
 * ### Nothing creates one yet
 *
 * There is no founding path, so no entity carries this today and the client ignores the message. Said out
 * loud because `world/SettlementLoreService` had to say the same thing and the worldgen module has shipped
 * three subsystems that were complete, tested and never reached. This is a deliberate scope line rather
 * than an oversight: founding needs a cost, an eligibility rule, a permission and a persisted row, none of
 * which are decided. What is here is the half those would plug into - the name resolution a founded area
 * needs is finished and exercised, because generated settlements go through the same registry and the same
 * [Place].
 */
class AreaName(
  name: String,
  /** How far the name reaches from the entity, in position units. */
  val radius: Long
) : DirtyableComponent() {

  var name: String = name
    set(value) {
      if (field == value) return
      field = value
      markDirty()
    }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return AreaNameComponentSMSG(entityId = entityId, name = name, radius = radius)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }
}
