package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.item.container.ItemHolder
import net.bestia.zone.util.AccountId

/**
 * Goods held by slots rather than carried by weight, on an entity whose [Inventory] is what it holds: a chest, a
 * guild's storage. Goods go in and out through the same handover as between a master and its bestias; this says
 * which container they land in, how much room it has and who may reach in.
 */
class SlotStorage(
  val holder: ItemHolder,
  var slots: Int,
  /** The masters who may reach in now, and how. */
  var users: Map<Long, Use>,
  /** The accounts that see what is inside. */
  var viewers: Set<AccountId>,
) : Component {

  data class Use(val mayPut: Boolean, val mayTake: Boolean) {
    companion object {
      val FULL = Use(mayPut = true, mayTake = true)
    }
  }

  fun useOf(masterId: Long): Use? {
    return users[masterId]
  }

  fun hasRoomFor(contents: Inventory, item: Inventory.Item): Boolean {
    return whatFits(contents, listOf(item), slots).isNotEmpty()
  }

  companion object {
    /** A chest is its builder's alone. */
    fun ofChest(chest: ChestStorage): SlotStorage {
      return SlotStorage(
        ItemHolder.Chest(chest.structureId),
        ChestStorage.SLOTS,
        mapOf(chest.ownerMasterId to Use.FULL),
        setOfNotNull(chest.ownerAccountId),
      )
    }

    /**
     * Which of [goods] a storage of [slots] holding [contents] takes. A pile that merges into one already in it takes
     * no slot. A storage over its limit keeps what it holds and takes nothing new.
     */
    fun whatFits(contents: Inventory, goods: List<Inventory.Item>, slots: Int): List<Inventory.Item> {
      var freeSlots = slots - contents.size()
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
