package net.bestia.zone.cartography

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.cartography.render.PlaceNames
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component

/** The names of the generated settlements, by settlement index, as the map shows them. */
@Component
class SettlementNames(private val worldService: WorldService) {

  val all: Map<Int, String> by lazy {
    val chronicle = worldService.generated.world.chronicle
    worldService.generated.world.features.all()
      .asSequence()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .mapNotNull { marker ->
        val name = PlaceNames.nameOf(chronicle, marker) ?: return@mapNotNull null
        marker.attribute(SettlementChannels.INDEX).toInt() to name
      }
      .toMap()
  }

  fun nameOf(settlement: Int): String? {
    return all[settlement]
  }
}
