package net.bestia.zoneserver.map.generator

import de.tfelix.bestia.worldgen.io.MapGenDAO
import de.tfelix.bestia.worldgen.map.Map2DDiscreteCoordinate
import de.tfelix.bestia.worldgen.map.Map2DDiscretePart
import de.tfelix.bestia.worldgen.map.MapDataPart
import de.tfelix.bestia.worldgen.random.NoiseVector
import de.tfelix.bestia.worldgen.workload.Job
import mu.KotlinLogging
import net.bestia.model.geometry.Rect
import net.bestia.model.map.MapDataDTO
import net.bestia.zoneserver.map.MapService

private val LOG = KotlinLogging.logger { }

/**
 * Generates some sample tiles for the bestia map.
 *
 * @author Thomas Felix
 */
class TileSaveJob(
    private val mapService: MapService
) : Job() {

  override fun foreachNoiseVector(dao: MapGenDAO, data: MapDataPart, vec: NoiseVector) {
    // no op.
  }

  override fun onFinish(dao: MapGenDAO?, data: MapDataPart?) {
    LOG.debug("Starting tile saving job.")

    val part = data!!.mapPart as Map2DDiscretePart

    val partRect = Rect(part.x, part.y, part.width, part.height)
    val mapDataDto = MapDataDTO(partRect)

    // Transfer the data into the DTO object.
    for (y in part.y until part.y + part.height) {
      for (x in part.x until part.x + part.width) {

        val noise = data.getCoordinateNoise(Map2DDiscreteCoordinate(x, y))

        if (noise == null) {
          LOG.warn("No tile data for x: {}, y: {}. Setting default gid {}.", x, y, DEFAULT_GID)
          mapDataDto.putGroundLayer(x, y, DEFAULT_GID)
          continue
        }

        val gid = noise.getValueInt(MapGeneratorConstants.TILE_MAP)
        mapDataDto.putGroundLayer(x, y, gid)

      }
    }
    // Now the tiles must be saved.
    LOG.info("Mapdata {} saved to database.", data)
    mapService.saveMapData(mapDataDto)
  }

  override fun onStart() {
    LOG.debug("Starting tile saving job.")
  }

  companion object {
    private const val DEFAULT_GID = 0
  }
}
