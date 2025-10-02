package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.Component


data class Loot(
  val itemId: Long,
  val amount: Int,
  val uniqueId: Long = 0 // 0 means nothing special.
) : Component