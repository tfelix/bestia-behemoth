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

  fun hasRoomFor(contents: Inventory, item: Inventory.Item): Boolean {
    return whatFits(contents, listOf(item)).isNotEmpty()
  }

  companion object {
    const val SLOTS = 20

    /** Which of [goods] a chest holding [contents] takes. A pile that merges into one already in it takes no slot. */
    fun whatFits(contents: Inventory, goods: List<Inventory.Item>): List<Inventory.Item> {
      var freeSlots = SLOTS - contents.size()
      val piles = contents.getItems().filter { it.isStackable }.mapTo(HashSet()) { it.itemId to it.provenance }

      return goods.filter { item ->
        val pile = item.itemId to item.provenance
        when {
          item.isStackable && pile in piles -> true
          freeSlots <= 0 -> false
          else -> {
            freeSlots--
            if (item.isStackable) piles.add(pile)
            true
          }
        }
      }
    }
  }
}
