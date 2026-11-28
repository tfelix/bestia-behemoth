package net.bestia.zone.fishing

import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.spawn.SpawnCorruption
import net.bestia.zone.spawn.ecs.ambient.AmbientSiteResolver
import net.bestia.zone.spawn.isCorrupted
import net.bestia.zone.weather.WeatherService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Service

/**
 * The [FishingSpot] the generated world, the weather and the clock make of a line. Asked once per catch, on the
 * tick: a handful of point samples.
 */
@Service
class GeneratedFishingGround(
  private val worldService: WorldService,
  private val water: RegionGrid,
  private val weather: WeatherService,
  private val clock: BestiaClock,
  private val sites: AmbientSiteResolver,
  private val corruption: SpawnCorruption,
) : FishingGround {

  override fun spotAt(castFrom: Vec3L, bobber: Vec3L): FishingSpot {
    if (!worldService.isLoaded) {
      return FishingSpot.UNKNOWN
    }

    val generated = worldService.generated
    val metres = generated.config.voxelSize
    val standX = castFrom.x * metres
    val standY = castFrom.y * metres
    val surface = generated.materializer.surface
    val elevation = generated.base.heightAt(bobber.x * metres, bobber.y * metres)
    val corrupted = surface.corruptionAt(standX, standY) >= CorruptionStage.CORRUPTED ||
        isCorrupted(corruption, castFrom.x, castFrom.y)

    return FishingSpot(
      water = water.waterKindAt(bobber),
      biome = surface.biomeAt(standX, standY),
      weather = weather.at(bobber.x, bobber.y, elevation).state.kind,
      isNight = clock.now().isNight,
      corrupted = corrupted,
    )
  }

  override fun levelsAt(position: Vec3L): IntRange? {
    if (!worldService.isLoaded) {
      return null
    }

    val metres = worldService.generated.config.voxelSize
    return sites.levelsAt(position.x * metres, position.y * metres)
  }
}
