package net.bestia.zoneserver.actor.entity.transmit

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityId

/**
 * This filter determines if a component should be transmitted to one
 * or multiple clients. This transmit calls happen in multiple stages
 * in order to fit into the actor world.
 *
 * First we determine a possible list of receiving entities. Usually
 * it make sense if these are connected and active clients in visual
 * range of this entity.
 *
 * These entities are then fetched from the system so their components
 * can be examined in order to provide a final decision to send out
 * the update to them.
 */
interface TransmitFilter {
  /**
   * @returns A list with entity IDs which belong to clients and might receive this component
   * update. If null is returned no lookup is performed and only the component
   * owning entity is provided as a candidate to the [selectTransmitTargetAccountIds] method.
   */
  fun findTransmitCandidates(transmit: TransmitRequest): Set<EntityId>

  fun selectTransmitTargetAccountIds(candidates: Set<Entity>, transmit: TransmitRequest): Set<Long>
}

