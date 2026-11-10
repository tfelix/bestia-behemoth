package net.bestia.zone.breeding

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.prop.StationSpill
import net.bestia.zone.prop.StructureEntry
import org.springframework.stereotype.Component

/**
 * A farm knocked down drops its held bundle and its egg where it stood. The pair is let go by
 * [net.bestia.zone.breeding.ecs.BreedingFarmSystem] once it finds the farm gone.
 */
@Component
class BreedingFarmSpill(
  private val farms: BreedingFarms,
  private val itemTemplates: ItemTemplateRegistry,
  private val lootItems: LootItemEntitySpawner,
) : StationSpill {

  override fun spill(world: World, station: StructureEntry, at: Vec3L) {
    if (station.kind != StaticEntityKind.BREEDING_FARM) return
    val farm = farms.of(station.id)

    if (farm.hasBundle) {
      itemTemplates.idOf(BreedingFarmService.NESTING_BUNDLE)
        ?.let { lootItems.spawnLootItem(world, itemId = it, amount = 1, pos = at) }
    }
    farm.egg?.let { egg ->
      itemTemplates.idOf(BreedingFarmService.BESTIA_EGG)
        ?.let { lootItems.spawnLootItem(world, itemId = it, amount = 1, pos = at, uniqueId = egg.instanceId) }
    }

    farms.put(farm.copy(phase = FarmPhase.IDLE, hasBundle = false, egg = null))
  }
}
