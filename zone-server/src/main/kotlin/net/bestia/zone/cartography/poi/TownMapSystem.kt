package net.bestia.zone.cartography.poi

import net.bestia.zone.cartography.render.MapService
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component as SpringComponent

/**
 * Puts every standing player town on the map: its sign under the town's name, and its post office and auction
 * house as services of it. Rebuilt on a cadence, because the registries it reads are tick-thread only and the map
 * is read from other threads.
 */
@SpringComponent
class TownMapSystem(
  private val towns: TownRegistry,
  private val structures: PlayerStructureRegistry,
  private val pois: MapPoiIndex,
  private val worldService: WorldService,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(10f)
  override val phase = Phase.UPKEEP

  override fun update(world: World, deltaTime: Float) {
    val metres = worldService.config.voxelSize
    val standing = towns.all().filter { it.isStanding }

    val signs = standing.map { town ->
      MapPoi(-town.id, MapService.TOWN, null, town.sign.x * metres, town.sign.y * metres, town.name, null, null)
    }
    val buildings = structures.all()
      .filter { !it.isUnderConstruction && it.kind in SERVICE_OF_KIND }
      .mapNotNull { building ->
        val town = towns.standingAt(building.position) ?: return@mapNotNull null
        MapPoi(
          -(STRUCTURE_ID_OFFSET + building.id), SERVICE_OF_KIND.getValue(building.kind), null,
          building.position.x * metres, building.position.y * metres, town.name, null, null
        )
      }

    pois.setPlayerTowns(signs + buildings)
  }

  private companion object {
    /** A player town's points are negative so they meet no prop or entity id, and a building's meet no sign's. */
    const val STRUCTURE_ID_OFFSET = 1_000_000_000L

    /** A player auction house is the town's market, as the start towns' market halls are. */
    val SERVICE_OF_KIND = mapOf(
      StaticEntityKind.POST_OFFICE to MapService.POST_OFFICE,
      StaticEntityKind.AUCTION_HOUSE to MapService.MARKET,
    )
  }
}
