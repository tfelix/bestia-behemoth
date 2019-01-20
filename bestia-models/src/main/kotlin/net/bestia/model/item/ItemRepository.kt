package net.bestia.model.item

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
interface ItemRepository : JpaRepository<Item, Int> {

  /**
   * Returns an item by its item database name. The name is unique. Returns null if the item was not found.
   *
   * @param itemDbName
   * Unique database name of an item.
   * @return The found item. Or null if the item was not found.
   */
  @Query("SELECT i FROM Item i where i.itemDbName = :name")
  fun findItemByName(@Param("name") itemDbName: String): Item?
}
