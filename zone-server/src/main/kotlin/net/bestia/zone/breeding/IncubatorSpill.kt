package net.bestia.zone.breeding

import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.loot.LootItemEntitySpawner
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.StationSpill
import net.bestia.zone.prop.StructureEntry
import org.springframework.stereotype.Component
import java.time.Clock

/** An incubator knocked down drops its egg where it stood, keeping the progress the egg made. */
@Component
class IncubatorSpill(
  private val incubators: Incubators,
  private val service: IncubatorService,
  private val itemTemplates: ItemTemplateRegistry,
  private val lootItems: LootItemEntitySpawner,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val clock: Clock = Clock.systemUTC(),
) : StationSpill {

  override fun spill(world: World, station: StructureEntry, at: Vec3L) {
    if (station.kind != StaticEntityKind.INCUBATOR) return
    val incubator = incubators.of(station.id)
    val egg = incubator.egg ?: return
    val eggItemId = itemTemplates.idOf(BreedingFarmService.BESTIA_EGG) ?: return

    val hatchedMillis = incubator.hatchedMillisAt(clock.instant())
    asyncJobExecutor.submit(station.id) { service.saveProgress(egg.instanceId, hatchedMillis) }
    lootItems.spawnLootItem(world, itemId = eggItemId, amount = 1, pos = at, uniqueId = egg.instanceId)

    incubators.put(incubator.empty())
  }
}
