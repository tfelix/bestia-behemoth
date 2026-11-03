package net.bestia.zone.casting

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.skill.persistence.SkillReagent
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Takes the item a skill uses up from what its caster carries: a master's inventory, or an owned bestia's own bag.
 * A wild caster has nothing to pay from and casts for free, which its learnset is balanced around.
 */
@Component
class ReagentPayment(
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /** Returns false, taking nothing, when a player's caster lacks the reagent. */
  fun pay(world: World, casterId: EntityId, reagent: SkillReagent?): Boolean {
    if (reagent == null) return true
    val owner = ItemOwner.of(world, casterId) ?: return true
    val inventory = world.get(casterId, Inventory::class) ?: return false

    val taken = inventory.takeAcrossPiles(reagent.itemId, reagent.amount) ?: return false

    asyncJobExecutor.submit(key = owner.masterId) {
      if (!inventoryService.consumeAll(owner.masterId, listOf(reagent.itemId to reagent.amount), owner.playerBestiaId)) {
        // Already spent in the world; the database only disagrees when something raced it, which the log shows.
        LOG.warn { "Master ${owner.masterId} lost reagent ${reagent.item} x${taken.sumOf { it.amount }} in the database" }
      }
    }

    return true
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
