package net.bestia.zone.item

import net.bestia.zone.battle.ecs.exp.GainExp
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.update
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The experience a bestia earns for gathering: `2 * resource_level + 5` per unit, the resource level being the
 * item's. See `bestia-docs/content/docs/mechanics/bestias/bestia.md`, Environment Interaction.
 */
@Component
class GatheringExp(
  private val itemStats: ItemStatCatalog,
) {

  /** Only owned bestias earn it; a master gathering earns nothing yet. */
  fun award(world: World, gathererId: EntityId, itemId: Long, amount: Int) {
    if (amount <= 0 || !world.has(gathererId, OwnedBestia::class)) return

    val exp = amount * (2 * itemStats.itemLevelOf(itemId) + 5)
    // Deferred like a kill's EXP, so two awards in one tick add up instead of the second add replacing the first.
    world.defer { world.update(gathererId, { GainExp() }) { it.value += exp } }
  }

  companion object {
    val READS: ComponentClassSet = setOf(OwnedBestia::class)
    val WRITES: ComponentClassSet = setOf(GainExp::class)
  }
}
