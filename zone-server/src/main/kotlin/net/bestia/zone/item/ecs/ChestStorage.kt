package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.AccountId

/**
 * Marks a chest's prop, whose [Inventory] is what the chest holds. Only the master [ownerMasterId] reaches into
 * it, and only [ownerAccountId] sees what is inside: its [SlotStorage] says so to the handover.
 */
data class ChestStorage(
  val structureId: Long,
  val ownerMasterId: Long,
  val ownerAccountId: AccountId?,
) : Component {

  companion object {
    const val SLOTS = 20

    /** Which of [goods] a chest holding [contents] takes. */
    fun whatFits(contents: Inventory, goods: List<Inventory.Item>): List<Inventory.Item> {
      return SlotStorage.whatFits(contents, goods, SLOTS)
    }
  }
}
