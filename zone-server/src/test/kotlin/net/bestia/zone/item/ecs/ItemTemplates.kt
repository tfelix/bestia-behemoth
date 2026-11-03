package net.bestia.zone.item.ecs

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository

/** A template registry that knows exactly [items]. */
fun itemTemplatesOf(vararg items: Item): ItemTemplateRegistry {
  return ItemTemplateRegistry(mockk<ItemRepository> { every { findAll() } returns items.toList() })
}
