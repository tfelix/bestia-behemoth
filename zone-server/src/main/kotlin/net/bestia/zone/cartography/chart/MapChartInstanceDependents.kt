package net.bestia.zone.cartography.chart

import net.bestia.zone.cartography.persistence.MapChartRepository
import net.bestia.zone.item.container.ItemInstanceDependents
import org.springframework.stereotype.Component

/** A chart's surveyed ground is a row keyed by its item instance, so it goes before the instance. */
@Component
class MapChartInstanceDependents(
  private val mapChartRepository: MapChartRepository,
) : ItemInstanceDependents {

  override fun deleteFor(instanceIds: Collection<Long>) {
    mapChartRepository.deleteAllByItemInstanceIdIn(instanceIds)
  }
}
