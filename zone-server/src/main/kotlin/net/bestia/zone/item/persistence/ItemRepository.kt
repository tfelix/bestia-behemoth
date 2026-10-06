package net.bestia.zone.item.persistence

import net.bestia.zone.item.persistence.Item.ItemType
import org.springframework.data.jpa.repository.JpaRepository
import net.bestia.zone.item.ItemNotFoundException

interface ItemRepository : JpaRepository<Item, Long> {
  fun findItemByType(type: ItemType): List<Item>
  fun findByIdentifier(itemIdentifier: String): Item?
}

fun ItemRepository.findByIdentifierOrThrow(itemIdentifier: String): Item {
  return findByIdentifier(itemIdentifier)
    ?: throw ItemNotFoundException(itemIdentifier)
}