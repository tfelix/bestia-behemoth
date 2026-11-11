package net.bestia.zone.casting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Puts a Trader's street stall down where they stand. The trade slice implements it. */
interface StallPitching {

  /** @return the refusal, or null once the stall stands */
  fun pitch(world: World, casterId: EntityId, maxListings: Int, seconds: Int): OpError?
}
