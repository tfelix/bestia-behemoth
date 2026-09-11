package net.bestia.zone.item.script

import net.bestia.zone.ecs.core.World
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId

/**
 * What using one item does.
 *
 * Runs on the caller's thread inside [net.bestia.zone.item.UseItemHandler]'s lock scope, so [world] is the
 * live world and every read is already safe - and every unbounded loop is already a stalled tick.
 *
 * @return whether the use happened. Only then is one consumed, so a script that refuses - no target, out of
 *   range, nothing to heal - leaves the item in the bag by returning false.
 */
interface ItemScript {
  val itemId: Long

  fun execute(world: World, userId: EntityId, args: ScriptArgs): Boolean
}
