package net.bestia.zone.item.script

import net.bestia.zone.ecs.Entity

interface ItemScript {
  val itemId: Long
  fun execute(user: Entity): Boolean
}

