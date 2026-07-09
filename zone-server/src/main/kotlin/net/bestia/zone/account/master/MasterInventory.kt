package net.bestia.zone.account.master

import jakarta.persistence.CascadeType
import jakarta.persistence.Embeddable
import jakarta.persistence.OneToMany
import net.bestia.zone.item.inventory.InventoryItem
import net.bestia.zone.item.Item

@Embeddable
class MasterInventory {

  @OneToMany(mappedBy = "master", cascade = [CascadeType.ALL])
  private val _items: MutableSet<InventoryItem> = mutableSetOf()

  val items: List<InventoryItem> get() = _items.toList()

  fun addItem(master: Master, item: Item, amount: Int) {
    val existing = _items.firstOrNull { it.playerItem.identifier == item.identifier && it.playerBestia == null }

    if (existing != null) {
      existing.amount += amount
    } else {
      _items.add(InventoryItem(master, item, amount))
    }
  }

  fun removeItem(itemIdentifier: String, amount: Int): Boolean {
    require(amount >= 0) {
      "Amount must be bigger or equal than 0"
    }

    val ownedItem = items.firstOrNull { it.playerItem.identifier == itemIdentifier }
    if (ownedItem == null) {
      return false
    } else {
      if (ownedItem.amount < amount) {
        return false
      }
      ownedItem.amount -= amount

      if (ownedItem.amount <= 0) {
        _items.remove(ownedItem)
      }

      return true
    }
  }

  fun hasItem(itemIdentifier: String, minAmount: Int): Boolean {
    val ownedAmount = items.firstOrNull { it.playerItem.identifier == itemIdentifier }?.amount
      ?: return false

    return ownedAmount >= minAmount
  }
}
