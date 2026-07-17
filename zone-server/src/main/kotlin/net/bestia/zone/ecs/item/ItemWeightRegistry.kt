package net.bestia.zone.ecs.item

import net.bestia.zone.item.ItemRepository
import org.springframework.stereotype.Component

@Component
class ItemWeightRegistry(
  itemRepository: ItemRepository
) {

  private val weightByItemId = itemRepository.findAll()
    .associate { it.id to it.weight }

  fun getWeight(itemId: Long): Int? {
    return weightByItemId[itemId]
  }
}