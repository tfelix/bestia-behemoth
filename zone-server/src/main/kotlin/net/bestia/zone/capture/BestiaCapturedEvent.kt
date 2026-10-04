package net.bestia.zone.capture

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.AccountId

/**
 * A wild bestia was caught and has already left the world; [BestiaAdoptionService] makes it the master's.
 *
 * An event rather than a call because the trap system lives inside the ECS world, and anything that can spawn
 * an entity depends on that world.
 */
data class BestiaCapturedEvent(
  val ownerAccountId: AccountId,
  val masterId: Long,
  val speciesId: Long,
  val at: Vec3L,
)
