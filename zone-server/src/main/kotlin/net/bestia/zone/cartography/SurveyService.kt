package net.bestia.zone.cartography

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.cartography.chart.ChartService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Shows a chart written off the tick in the live inventory, for surveys, merges and copies alike.
 *
 * ### Why the live inventory is corrected rather than left to resync
 *
 * The ECS [Inventory] is what the client sees and what carry capacity is computed from, and the row is what
 * survives a restart. A write that only did the second would show the player nothing until they logged out, and
 * would let them carry an unbounded number of charts in the meantime. `CraftingService.applyToTarget` says the
 * same thing about the same pair.
 *
 * Unlike the crafting path, this corrects the mirror *after* the write rather than before, and gets something
 * for it: the instance id is only known once the row exists, so the client learns the chart's real `uniqueId`
 * immediately instead of a placeholder zero it would have to see corrected on the next full send. A chart is
 * addressed by that id when it is merged or copied, so a placeholder would be a chart the player could hold but
 * not use.
 */
@Service
class SurveyService(
  private val world: WorldView,
) {

  /**
   * Mirrors a completed chart operation onto the live inventory component.
   *
   * Takes a world scope from off the tick, which is how an IO-lane handler reaches the world too. It waits
   * until the tick thread lends the world, which is why this is called from an async worker and never from the
   * tick itself.
   */
  fun applyToLiveInventory(
    entityId: EntityId,
    result: ChartService.Result.Ok,
    removedUniqueIds: List<Long> = emptyList()
  ) {
    world.modify(entityId) { id ->
      val inventory = get(id, Inventory::class)
      if (inventory == null) {
        // Logged out between the survey resolving and the row being written. The chart is on the row, so it is
        // there on the next login; only the live mirror is missed.
        LOG.debug { "Entity $entityId has no inventory to show chart ${result.uniqueId} in" }
        return@modify
      }

      result.consumedBlankPaperItemId?.let { inventory.decItem(it.toInt()) }
      removedUniqueIds.forEach { inventory.removeByUniqueId(it) }

      // A chart never stacks - it carries per-instance state, which is the whole reason it has an instance -
      // and never wears, so it has no durability to report.
      inventory.addItem(
        Inventory.Item(
          itemId = result.item.id,
          amount = 1,
          weight = result.item.weight,
          uniqueId = result.uniqueId,
          stackable = false,
          durability = 0,
          maxDurability = 0
        )
      )
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
