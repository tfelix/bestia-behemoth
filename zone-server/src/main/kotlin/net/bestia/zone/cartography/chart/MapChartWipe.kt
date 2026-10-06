package net.bestia.zone.cartography.chart

import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import net.bestia.zone.cartography.persistence.MapChartRepository

/**
 * Charts name places by coordinate, and the coordinates mean different terrain in the new world.
 * `MapChart.worldShapeVersion` would catch a survived row and refuse to read it, so this is the tidy half
 * rather than the correctness half - but leaving them would keep an unreadable item in every inventory.
 */
@Component
@Order(1)
class MapChartWipe(
  private val mapChartRepository: MapChartRepository,
) : WorldScopedData {

  override fun wipe() {
    mapChartRepository.deleteAll()
  }
}
