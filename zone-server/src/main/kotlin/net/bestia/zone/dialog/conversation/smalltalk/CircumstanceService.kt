package net.bestia.zone.dialog.conversation.smalltalk

import net.bestia.worldgen.climate.WeatherKind
import net.bestia.zone.dialog.conversation.Speaker
import net.bestia.zone.ecs.place.PlaceRegionService
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.Season
import net.bestia.zone.environment.weather.WeatherService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * What is true around a speaker, gathered from the four systems that already know.
 *
 * Everything is read at the *town* rather than at the entity. A settlement is smaller than a weather
 * region and much smaller than a place region, so the answers are the same either way - and reading the
 * town means a conversation needs no position, no loaded chunk and no live entity to be resolvable.
 */
@Service
class CircumstanceService(
  private val worldService: WorldService,
  private val sites: SettlementSiteIndex,
  private val regions: PlaceRegionService,
  private val weather: WeatherService,
  private val clock: BestiaClock,
) {

  fun of(speaker: Speaker): Circumstance? {
    val site = sites.siteOf(speaker.settlement) ?: return null
    val record = worldService.generated.world.chronicle.settlements.getOrNull(speaker.settlement)
      ?: return null

    val now = clock.now()
    val config = worldService.generated.config

    return Circumstance(
      occupation = speaker.occupation.id,
      age = speaker.member.age,
      kinship = speaker.member.kinship,
      terrain = regions.regions.regionAt(site.centre.x, site.centre.y).kind,
      // The hemisphere matters: without it every town on the map has its winter in the same month, and
      // half the world's small talk is about the wrong season.
      season = Season.at(now.yearProgress, site.centre.y / config.heightMetres),
      weather = weatherAt(site),
      walled = record.wallYear != 0,
      sacked = record.timesSacked > 0,
      wealth = record.wealth,
    )
  }

  /**
   * Today's weather over the town, or a clear sky if the town has no building to stand on.
   *
   * The elevation comes from a doorstep rather than from the chunk grid, because a town a player is
   * talking their way across may still have unloaded chunks at its edges - and a weather query that
   * sometimes returns nothing would make a line about the rain appear and disappear as people walk.
   */
  private fun weatherAt(site: SettlementSite): WeatherKind {
    val ground = site.buildings.firstOrNull()?.floorElevation ?: return WeatherKind.CLEAR
    val voxelSize = worldService.generated.config.voxelSize

    val at = weather.at(
      (site.centre.x / voxelSize).toLong(),
      (site.centre.y / voxelSize).toLong(),
      ground,
    )

    return at.state.kind
  }
}
