package net.bestia.model.dao

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import net.bestia.model.domain.Item
import org.springframework.data.jpa.repository.JpaRepository

@Repository
interface ItemDAO : JpaRepository<Item, Int> {

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
