package net.bestia.zone.message

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId

/**
 * Who a message about an entity or a place goes to. The interest areas that decide it live in slices above
 * this one, so they implement it.
 */
interface Recipients {

  /** The accounts told about [entityId]. Tick thread, or under the world lock. */
  fun observersOf(world: World, entityId: EntityId): Set<AccountId>

  /** The accounts whose interest area holds [pos]. */
  fun inRangeOf(pos: Vec3L): Set<AccountId>
}
