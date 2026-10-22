package net.bestia.zone.casting

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * Lets an entity cast a skill it never learned itself, such as one its guild learned. A port: the slices that
 * grant skills sit above this one. Read-only, on the tick.
 */
interface SkillGrant {
  /** The level of [skillId] [casterId] may cast without having learned it; 0 for none. */
  fun grantedLevel(world: World, casterId: EntityId, skillId: Long): Int
}
