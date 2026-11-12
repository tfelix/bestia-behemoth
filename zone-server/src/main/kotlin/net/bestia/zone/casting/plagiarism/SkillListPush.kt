package net.bestia.zone.casting.plagiarism

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Sends a master's player their skill list again, after a copy came or went. A port: the list is built in `master`. */
interface SkillListPush {
  fun push(world: World, masterEntityId: EntityId)
}
