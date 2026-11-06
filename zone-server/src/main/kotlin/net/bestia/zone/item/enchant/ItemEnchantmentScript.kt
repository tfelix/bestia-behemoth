package net.bestia.zone.item.enchant

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** What one enchantment does, found by the `script` key of `item_enchantments.yml`. */
interface ItemEnchantmentScript {

  /** The item has just fallen to the ground as [groundEntity], on the tick. Not called when it is loaded again. */
  fun onDropped(world: World, groundEntity: EntityId, enchantment: ActiveItemEnchantment) {
  }

  /** A Dispel broke [enchantment] on [groundEntity], on the tick. It is already gone from the ground item. */
  fun onDispelled(world: World, groundEntity: EntityId, enchantment: ActiveItemEnchantment) {
  }
}
