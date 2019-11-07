package net.bestia.model.test

import net.bestia.model.item.Item
import net.bestia.model.item.ItemRepository
import net.bestia.model.item.ItemType

object ItemFixture {
  @JvmStatic
  fun createItem(
      itemDbName: String,
      itemRepository: ItemRepository
  ): Item {
    return Item(
        databaseName = itemDbName,
        mesh = itemDbName,
        type = ItemType.ETC
    ).also { itemRepository.save(it) }
  }
}