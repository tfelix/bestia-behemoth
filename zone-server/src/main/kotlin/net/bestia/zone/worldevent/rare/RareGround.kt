package net.bestia.zone.worldevent.rare

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.PlaceRegionService
import net.bestia.zone.weather.WeatherService
import net.bestia.zone.world.WorldService
import net.bestia.zone.worldevent.EventGround
import org.springframework.stereotype.Component

/**
 * The land a rare bestia may come down on: the centre of each land place region, with the biome most of it has. A
 * region is about 6 km across, so one spot per region is cheap enough to ask every director run.
 */
@Component
class RareGround(
  private val placeRegions: PlaceRegionService,
  private val worldService: WorldService,
  private val ground: EventGround,
  private val weather: WeatherService,
) {

  /** A region centre in metres, with its biome. */
  data class Spot(val regionIndex: Int, val x: Double, val y: Double, val biome: Biome)

  fun spotsOf(regionIndices: List<Int>): List<Spot> {
    val regions = placeRegions.regions.regions

    return regionIndices.map { regions[it] }.filterNot { it.isWater }.map {
      Spot(it.index, it.centre.x, it.centre.y, it.dominantBiome)
    }
  }

  fun allSpots(): List<Spot> = spotsOf(placeRegions.regions.regions.indices.toList())

  /** Where a bestia stands on [spot], or null on water or where the ground is not known. */
  fun grounded(spot: Spot): Vec3L? = ground.grounded(spot.x, spot.y)

  fun weatherAt(at: Vec3L): WeatherKind =
    weather.at(at.x, at.y, worldService.config.elevationOfVoxel(at.z.toInt())).state.kind
}
