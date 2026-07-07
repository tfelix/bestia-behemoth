package net.bestia.zone.item.script

import net.bestia.zone.ecs2.EntityId
import net.bestia.zone.ecs2.World

interface ItemScript {
  val itemId: Long
  fun execute(world: World, userId: EntityId): Boolean
}
