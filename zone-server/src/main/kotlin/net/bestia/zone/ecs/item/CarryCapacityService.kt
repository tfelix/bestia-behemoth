package net.bestia.zone.ecs.item

import net.bestia.zone.item.ItemRepository
import org.springframework.stereotype.Component

/**
 * Pure calculations backing [CarryCapacity]. See the game docs' weight-limit formula:
 * https://docs.bestia-game.net/docs/mechanics/items/#weight-limit
 */
@Component
class CarryCapacityService(
  private val itemRepository: ItemRepository
) {

  fun computeWeightLimit(strength: Int, vitality: Int, level: Int): Int {
    return strength / 2 + vitality / 5 + 15 + level / 5
  }

  fun computeCurrentWeight(items: List<Inventory.Item>): Int {
    return items.sumOf { item ->
      val itemDef = itemRepository.findById(item.itemId).orElse(null) ?: return@sumOf 0
      itemDef.weight * item.amount
    }
  }
}
