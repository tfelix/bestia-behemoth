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

  /** Returns false, taking nothing, when a player's caster lacks one of the reagents. */
  fun pay(world: World, casterId: EntityId, reagents: List<SkillReagent>): Boolean {
    if (reagents.isEmpty()) return true
    val owner = ItemOwner.of(world, casterId) ?: return true
    val inventory = world.get(casterId, Inventory::class) ?: return false

    val holdsAll = reagents.all { reagent ->
      inventory.getItems().filter { it.itemId == reagent.itemId }.sumOf { it.amount } >= reagent.amount
    }
    if (!holdsAll) return false

    reagents.forEach { inventory.takeAcrossPiles(it.itemId, it.amount) }
    val spent = reagents.map { it.itemId to it.amount }

    asyncJobExecutor.submit(key = owner.masterId) {
      if (!inventoryService.consumeAll(owner.masterId, spent, owner.playerBestiaId)) {
        // Already spent in the world; the database only disagrees when something raced it, which the log shows.
        LOG.warn { "Master ${owner.masterId} lost reagents $spent (item id to amount) in the database" }
      }
    }

    return true
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
