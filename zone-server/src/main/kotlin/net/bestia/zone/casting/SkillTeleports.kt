package net.bestia.zone.casting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** Teleport and Warp Portal for the casting slice; the teleport slice implements it. */
interface SkillTeleports {

  /**
   * Sends the caster, and its own bestias within reach, to a random spot nearby or home. The trip leaves a
   * teleport sickness, during which nothing teleports them again.
   *
   * @return the refusal, or null once they are on their way
   */
  fun teleport(world: World, casterId: EntityId, toHome: Boolean): OpError?

  /**
   * Opens a portal at [at] to [to], or to the caster's home when [to] is null. It lets [passages] bodies through,
   * either way, for [seconds].
   */
  fun openPortal(world: World, casterId: EntityId, at: Vec3L, to: Vec3L?, passages: Int, seconds: Int): OpError?
}
