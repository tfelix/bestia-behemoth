package net.bestia.zone.stronghold.ecs

import net.bestia.zone.cartography.poi.MapPoi
import net.bestia.zone.cartography.poi.MapPoiIndex
import net.bestia.zone.cartography.render.MapService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.stronghold.EmpyriteSeedDiscoveries
import net.bestia.zone.stronghold.EmpyriteSeedRegistry
import net.bestia.zone.stronghold.StrongholdRegistry
import org.springframework.stereotype.Component as SpringComponent

/**
 * Puts every discovered Empyrite Seed and every Stronghold on the map, named after its seed, as `TownMapSystem` does
 * the player towns: the registries are the tick's, the map is read off it. A Stronghold is on the map from its
 * planting, discovered or not, since its guild walked onto the core to plant it.
 */
@SpringComponent
class SeedMapSystem(
  private val seeds: EmpyriteSeedRegistry,
  private val discoveries: EmpyriteSeedDiscoveries,
  private val strongholds: StrongholdRegistry,
  private val pois: MapPoiIndex,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(10f)
  override val phase = Phase.UPKEEP

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    val onMap = seeds.awake().mapNotNull { seed ->
      val kind = when {
        strongholds.on(seed.rank) != null -> MapService.STRONGHOLD
        discoveries.isDiscoveredByAnyone(seed.rank) -> MapService.EMPYRITE_SEED
        else -> return@mapNotNull null
      }
      MapPoi(ID_BASE - seed.rank, kind, null, seed.x, seed.y, seed.name, null, null)
    }

    pois.setStrongholdSeeds(onMap)
  }

  private companion object {
    /** Below the towns' negative ids, so a seed never shares one with a town. */
    const val ID_BASE = -1_000_000_000L
  }
}
