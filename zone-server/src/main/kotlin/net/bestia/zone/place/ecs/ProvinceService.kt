package net.bestia.zone.place.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.place.Province
import net.bestia.worldgen.place.Provinces
import net.bestia.zone.world.WorldRecreatedEvent
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaSanctuaries
import net.bestia.zone.world.settlement.StartTownMarkets
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service

/**
 * The world's provinces, built once per world from the place regions and held in memory, as
 * [PlaceRegionService] holds the regions. A start town's province is a sanctuary.
 */
@Service
class ProvinceService(
  private val worldService: WorldService,
  private val placeRegions: PlaceRegionService,
  private val startTowns: StartTownMarkets,
) : ManaSanctuaries {

  @Volatile
  private var cached: Provinces? = null

  val provinces: Provinces
    get() {
      cached?.let { return it }

      synchronized(this) {
        cached?.let { return it }

        val built = Provinces.of(worldService.generated.world, placeRegions.regions)
        LOG.info {
          "Provinces: ${built.count}, ${built.provinces.count(::isSanctuary)} sanctuary, " +
            "partition ${built.partitionVersion}"
        }

        cached = built
        return built
      }
    }

  fun provinceAt(x: Double, y: Double): Province? = provinces.provinceAt(x, y)

  fun isSanctuary(province: Province): Boolean {
    val capital = province.seat.settlementIndex
    return capital >= 0 && startTowns.isStartTown(capital)
  }

  override fun isSanctuaryAt(x: Double, y: Double): Boolean = provinceAt(x, y)?.let(::isSanctuary) == true

  @EventListener
  @Order(WorldRecreatedEvent.ListenerOrder.PROVINCES)
  fun handleWorldRecreated(event: WorldRecreatedEvent) {
    cached = null
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
