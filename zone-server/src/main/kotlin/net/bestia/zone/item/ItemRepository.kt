package net.bestia.zone.item

import net.bestia.zone.item.Item.ItemType
import org.springframework.data.jpa.repository.JpaRepository

interface ItemRepository : JpaRepository<Item, Long> {
  fun findItemByType(type: ItemType): List<Item>
  fun findByIdentifier(itemIdentifier: String): Item?
}

fun ItemRepository.findByIdentifierOrThrow(itemIdentifier: String): Item {
  return findByIdentifier(itemIdentifier)
    ?: throw ItemNotFoundException(itemIdentifier)
}