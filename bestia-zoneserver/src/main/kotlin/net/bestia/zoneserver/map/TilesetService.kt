package net.bestia.zoneserver.map

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import net.bestia.model.map.Tileset
import net.bestia.model.map.TilesetDataRepository
import org.springframework.stereotype.Service
import java.io.IOException

private val LOG = KotlinLogging.logger { }

@Service
class TilesetService(
    private val mapper: ObjectMapper,
    private val tilesetRepository: TilesetDataRepository
) {

  fun findTileset(containedGid: Int): Tileset? {
    val data = tilesetRepository.findByGid(containedGid.toLong())

    return try {
      mapper.readValue(data.data, Tileset::class.java)
    } catch (e: IOException) {
      LOG.error("Could not deserialize the data {} for tileset {}.", data.data, data.id, e)
      null
    }
  }

  fun findAllTilesets(gids: Set<Int>): List<Tileset> {
    return gids.asSequence()
        .mapNotNull { findTileset(it) }
        .distinct()
        .toList()
  }
}
