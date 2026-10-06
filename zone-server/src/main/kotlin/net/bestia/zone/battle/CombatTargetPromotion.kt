package net.bestia.zone.battle

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * Makes a target that cannot be fought yet, a static prop, into one that can, on its first hit. The prop slice
 * owns props, so it implements this.
 */
interface CombatTargetPromotion {

  /**
   * @return true if [entityId] can now be fought, or already could; false if it stays as it is, and the caller
   *   treats it like any other target it cannot resolve.
   */
  fun promoteIfNeeded(world: World, entityId: EntityId, from: Vec3L, reach: Long): Boolean

  companion object {
    /**
     * How far from its actor a prop may be named as a target: about the draw distance that
     * `ChunkStreamConfig.viewRadiusChunks` is sized against, so a player can target whatever they see.
     */
    const val TARGETING_REACH = 200L
  }
}
