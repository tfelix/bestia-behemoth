package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.AccountId

/**
 * Marks a chest's prop, whose [Inventory] is what the chest holds. Only the master [ownerMasterId] reaches into
 * it, and only [ownerAccountId] sees what is inside.
 */
data class ChestStorage(
  val structureId: Long,
  val ownerMasterId: Long,
  val ownerAccountId: AccountId?,
) : Component {

  /** A pile that merges into one already in the chest takes no slot of its own. */
  fun hasRoomFor(contents: Inventory, item: Inventory.Item): Boolean {
    val mergesIntoPile = item.isStackable && contents.getItems().any {
      it.isStackable && it.itemId == item.itemId && it.provenance == item.provenance
    }

    return mergesIntoPile || contents.size() < SLOTS
  }

  companion object {
    const val SLOTS = 20
  }
}
