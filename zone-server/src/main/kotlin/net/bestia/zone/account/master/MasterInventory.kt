package net.bestia.zone.account.master

import jakarta.persistence.CascadeType
import jakarta.persistence.Embeddable
import jakarta.persistence.OneToMany
import net.bestia.zone.item.InventoryItem
import net.bestia.zone.item.Item

@Embeddable
class MasterInventory {

  @Transient
  internal lateinit var master: Master

  @OneToMany(mappedBy = "master", cascade = [CascadeType.ALL])
  private val items: MutableSet<InventoryItem> = mutableSetOf()

  val ownedItems: List<InventoryItem> get() = items.toList()

  fun addItem(item: Item, inventoryPolicy: InventoryPolicy) {
    inventoryPolicy.checkPolicy(master, item)

    items.add(InventoryItem(master, item))
  }

  fun removeItem(itemIdentifier: String, amount: Int): Boolean {
    require(amount >= 0) {
      "Amount must be bigger or equal than 0"
    }

    val ownedItem = items.firstOrNull { it.item.identifier == itemIdentifier }
    if (ownedItem == null) {
      return false
    } else {
      if (ownedItem.amount < amount) {
        return false
      }
      ownedItem.amount -= amount

      if (ownedItem.amount <= 0) {
        items.remove(ownedItem)
      }

      return true
    }
  }

  fun hasItem(itemIdentifier: String, minAmount: Int): Boolean {
    val ownedAmount = items.firstOrNull { it.item.identifier == itemIdentifier }?.amount
      ?: return false

    return ownedAmount >= minAmount
  }
}
