package net.bestia.zone.crafting.repair

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.prop.StructureSalvage
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.workexp.WorkExpService
import org.springframework.stereotype.Component
import kotlin.math.floor

/**
 * A share of the kit's materials and of its build time as work EXP, so clearing away what others left behind
 * pays a little. The EXP follows the build time, so building something only to knock it down never pays.
 */
@Component
class KitSalvage(
  private val kits: KitCatalogue,
  private val loot: LootItemEntitySpawner,
  private val workExp: WorkExpService,
) : StructureSalvage {

  override val reads: ComponentClassSet = workExp.reads
  override val writes: ComponentClassSet = workExp.writes

  override fun salvage(world: World, structure: StructureEntry, at: Vec3L, salvagerId: EntityId, salvagerAccountId: AccountId) {
    val kitCost = kits.repairCostOf(structure.kind) ?: return

    kitCost.materials.forEach { (itemId, amount) ->
      val salvaged = floor(amount * SALVAGE_SHARE).toInt()
      if (salvaged > 0) {
        loot.spawnLootItem(world, itemId = itemId, amount = salvaged, pos = at, lootOwner = salvagerAccountId)
      }
    }

    workExp.award(world, salvagerId, kitCost.buildSeconds * SALVAGE_SHARE)
  }

  companion object {
    const val SALVAGE_SHARE = 0.25
  }
}
