package net.bestia.zone.item.ecs

/**
 * Where [Inventory] finds the own name of an item copy, like a relic's. A higher slice that hands out named
 * copies installs itself at startup, so the item slice never names it.
 */
fun interface ItemTitles {

  fun of(uniqueId: Long): String?

  companion object {
    /** Read on every inventory sync, for the reason `MapCircles.installed` is static. */
    @Volatile
    var installed: ItemTitles = ItemTitles { null }
  }
}
