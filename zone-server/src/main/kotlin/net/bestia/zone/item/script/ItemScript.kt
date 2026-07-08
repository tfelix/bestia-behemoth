package net.bestia.zone.item.script

import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World

interface ItemScript {
  val itemId: Long
  fun execute(world: World, userId: EntityId): Boolean
}
