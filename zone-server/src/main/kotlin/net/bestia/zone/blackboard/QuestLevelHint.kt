package net.bestia.zone.blackboard

import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.LootItemRepository
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import org.springframework.stereotype.Component

/**
 * The level a blackboard quest suggests: the weakest species that drops the item, or for what nothing drops -
 * ore, herbs - the item's own tier. A suggestion only; the board never refuses a master by level.
 */
@Component
class QuestLevelHint(
  private val drops: LootItemRepository,
  private val species: BestiaCatalogue,
  private val itemTemplates: ItemTemplateRegistry,
) {

  private val weakestDropperLevel: Map<Long, Int> by lazy {
    drops.findAllDrops()
      .filter { it.dropChance > 0 }
      .mapNotNull { drop -> species.find(drop.bestiaId)?.let { drop.itemId to it.levelMin } }
      .groupBy({ it.first }, { it.second })
      .mapValues { (_, levels) -> levels.min() }
  }

  fun suggestedLevelOf(itemId: Long): Int {
    return weakestDropperLevel[itemId] ?: itemTemplates.baseLevelOf(itemId) ?: 1
  }
}
