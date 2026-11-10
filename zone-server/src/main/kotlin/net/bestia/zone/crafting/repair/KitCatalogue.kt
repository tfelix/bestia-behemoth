package net.bestia.zone.crafting.repair

import net.bestia.zone.crafting.RecipeRegistry
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.kit.StructureKitScript
import org.springframework.stereotype.Component

/** The kit behind each kind a player builds, and the recipe that makes it: what a repair of that kind costs. */
@Component
class KitCatalogue(
  private val kits: List<StructureKitScript>,
  private val recipes: RecipeRegistry,
  private val propKinds: PropKindRegistry,
) {

  /** Null for a kind no kit builds. */
  fun repairCostOf(kind: StaticEntityKind): RepairCost? {
    val kit = kits.firstOrNull { it.kind == kind } ?: return null
    val recipe = recipes.all().firstOrNull { it.output?.itemId == kit.itemId }
    val materials = recipe?.inputs.orEmpty().associate { it.itemId to it.amount }

    return RepairCost(propKinds.of(kind).maxHp, kit.buildSeconds, materials)
  }
}
