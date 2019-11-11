package net.bestia.zoneserver.map.generator

import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.map.MapCoordinate
import de.tfelix.bestia.worldgen.map.MapDataPart
import de.tfelix.bestia.worldgen.random.NoiseVector
import de.tfelix.bestia.worldgen.workload.Job
import mu.KotlinLogging

private val LOG = KotlinLogging.logger { }

/**
 * Generates some sample tiles for the bestia map.
 *
 * @author Thomas Felix
 */
class TileGenerationJob : Job() {

  private var waterCount = 0
  private var landCount = 0

  override fun foreachNoiseVector(dao: MapGenDAO, data: MapDataPart, vec: NoiseVector, cord: MapCoordinate) {
    val heightLevel = vec.getValueDouble(MapGeneratorConstants.HEIGHT_MAP)

    if (heightLevel < WATERLEVEL) {
      // Water tile.
      vec.setValue(MapGeneratorConstants.TILE_MAP, 11)
      waterCount++
    } else {
      // Land tile.
      vec.setValue(MapGeneratorConstants.TILE_MAP, 79)
      landCount++
    }

    LOG.debug { "Generated land tiles: $landCount, water tiles: $waterCount" }
  }

  override fun onStart() {
    LOG.debug("Starting tile generation job.")
    waterCount = 0
    landCount = 0
  }

  companion object {
    private const val WATERLEVEL = 800.0
  }

}
