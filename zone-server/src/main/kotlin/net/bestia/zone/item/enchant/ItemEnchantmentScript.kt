package net.bestia.zone.item.enchant

import net.bestia.zone.battle.status.StatusValueRecalcContext
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

  /** The item is held in the weapon hand while the wielder's status values are rebuilt. Not for an ended one. */
  fun onWielderRecalc(context: StatusValueRecalcContext, enchantment: ActiveItemEnchantment) {
  }
}
