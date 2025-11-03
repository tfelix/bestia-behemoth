package net.bestia.zone.item

import org.springframework.data.jpa.repository.JpaRepository

interface ItemRepository : JpaRepository<Item, Long> {
  fun findByIdentifier(itemIdentifier: String): Item?
}

fun ItemRepository.findByIdentifierOrThrow(itemIdentifier: String): Item {
  return findByIdentifier(itemIdentifier)
    ?: throw ItemNotFoundException(itemIdentifier)
}