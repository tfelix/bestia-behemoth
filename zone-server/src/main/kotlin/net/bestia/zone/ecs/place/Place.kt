package net.bestia.zone.ecs.place

import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.DirtyableComponent
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Where an entity is, in words. Owner-only.
 *
 * ### One place, and the server picks it
 *
 * Areas do overlap on the ground - a town sits inside a region, and two player claims can touch - but
 * "where am I" has one answer. [PlaceNameService] applies the rule (the smallest area containing the
 * point, or the region when none does) and only the winner is stored. A client handed a list would be
 * ranking places itself, which is a server rule in a second implementation.
 *
 * ### No publisher, and no memo
 *
 * `environment/weather/WeatherPublisher` keeps a per-account `lastSent` map to avoid re-sending an
 * unchanged answer. A component needs none of that: the dirty flag *is* the dedup, `SyncTargets` is the
 * targeting, and `ZoneEngine.syncDirtyComponents` already builds the message off the tick thread.
 *
 * There is no cached position here either, because `Position` already is one: [PlaceSystem] resolves only
 * for an entity whose `Position` is dirty this tick. Storing the coordinates twice would mean two
 * answers to where something is.
 */
class Place(place: PlaceRef) : DirtyableComponent() {

  /** Assigning an equal place is not a change, so an entity walking about inside one region never syncs. */
  var place: PlaceRef = place
    set(value) {
      if (field == value) return
      field = value
      markDirty()
    }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return PlaceComponentSMSG(entityId = entityId, name = place.name)
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}
