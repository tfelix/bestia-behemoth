package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.Component


data class Inventory(
  val items: MutableList<Item>
) : Component {

  class Item(
    val itemId: Int,
    val amount: Int,
    val uniqueId: Long = 0 // 0 means nothing special.
  )
}