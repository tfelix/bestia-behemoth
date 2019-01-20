package net.bestia.zoneserver.map

import mu.KotlinLogging
import net.bestia.model.map.MapDataRepository
import net.bestia.model.map.MapParameterRepository
import net.bestia.model.map.MapData
import net.bestia.model.geometry.Point
import net.bestia.model.geometry.Rect
import net.bestia.model.map.BestiaMap
import net.bestia.model.map.*
import net.bestia.model.util.ObjectSerializer
import org.springframework.stereotype.Service

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.util.*
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

private val LOG = KotlinLogging.logger { }

/**
 * The [MapService] is central instance for requesting and modifing map
 * data operation inside the bestia system. It is responsible for effectively
 * querying the cache in order to get map data from the in memory db since we
 * can not simply load the fully map into memory as a single map.
 *
 * @author Thomas Felix
 */
@Service
class MapService(
    private val mapDataDao: MapDataRepository,
    private val mapParamDao: MapParameterRepository,
    private val tilesetService: TilesetService
) {
  /**
   * Checks if there is a initialized map inside the permanent storage is
   * active.
   *
   * @return TRUE if the map is initialized. FALSE otherwise.
   */
  val isMapInitialized: Boolean
    get() = mapDataDao.count() > 0

  /**
   * Returns the name of the current map/world.
   *
   * @return The name of the map.
   */
  val mapName: String
    get() = mapParamDao.findFirstByOrderByIdDesc()?.name ?: ""

  /**
   * Retrieves and generates the map. It has the dimensions of the given
   * coordinates and contains all layers and tilemap data.
   *
   * @return A [BestiaMap] containig the enclosed data.
   */
  fun getMap(startX: Long, startY: Long, width: Long, height: Long): BestiaMap {
    if (startX < 0 || startY < 0 || width < 0 || height < 0) {
      throw IllegalArgumentException("X, Y, width and height must be positive.")
    }

    val rect = Rect(startX, startY, width, height)
    val data = getCoveredMapDataDTO(startX, startY, width, height)

    // Combine them to a big dto.
    val joinedData = data.reduce { obj, rhs -> obj.join(rhs) }
    val slicedData = joinedData.slice(rect)

    // Get all used tilesets.
    val gids = slicedData.distinctGids

    // Find all tilesets for the gids.
    val tilesets = tilesetService.findAllTilesets(gids)

    return BestiaMap(slicedData, tilesets)
  }

  /**
   * Alias for [.getMap].
   *
   * @param bbox
   * The bounding box for retrieving the map data.
   */
  fun getMap(bbox: Rect): BestiaMap {
    return getMap(bbox.x, bbox.y, bbox.width, bbox.height)
  }

  /**
   * Compresses the given byte stream.
   *
   * @param input
   * The byte stream of the compression.
   * @return The compressed byte stream.
   * @throws IOException
   * If the object could not be compressed.
   */
  @Throws(IOException::class)
  private fun compress(input: ByteArray): ByteArray {
    ByteArrayOutputStream(input.size).use { outputStream ->
      val deflater = Deflater(7)
      deflater.setInput(input)
      deflater.finish()

      val buffer = ByteArray(1024)
      while (!deflater.finished()) {
        val count = deflater.deflate(buffer)
        outputStream.write(buffer, 0, count)
      }

      deflater.end()

      return outputStream.toByteArray()
    }
  }

  @Throws(IOException::class)
  private fun uncompress(input: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(input, 0, input.size)
    ByteArrayOutputStream(input.size * 2).use { outputStream ->
      val buffer = ByteArray(1024)
      while (!inflater.finished()) {
        try {
          val count = inflater.inflate(buffer)
          outputStream.write(buffer, 0, count)
        } catch (e: DataFormatException) {
          // Rethrow as IO
          throw IOException(e)
        }
      }

      inflater.end()

      return outputStream.toByteArray()
    }
  }

  /**
   * Saved the given [MapDataDTO] to the database for later retrieval.
   *
   */
  @Throws(IOException::class)
  fun saveMapData(dto: MapDataDTO) {
    try {
      var output = ObjectSerializer.serialize(dto) ?: throw IOException()
      output = compress(output)

      LOG.debug("Map data {} compressed size: {} bytes.", dto, output.size)

      val mapData = MapData(
          data = output,
          height = dto.rect.height,
          width = dto.rect.width,
          x = dto.rect.x,
          y = dto.rect.y
      )
      mapDataDao.save(mapData)
    } catch (e: IOException) {
      LOG.error(e) { "Could not compress map data: $dto" }
      throw e
    }
  }

  /**
   * Returns a list with all DTOs which are covering the given range.
   *
   * @param x
   * X start of the range.
   * @param y
   * Y start of the range.
   * @param width
   * Width of the area.
   * @param height
   * Height of the area.
   * @return A list with all [MapDataDTO] covering the given area.
   */
  private fun getCoveredMapDataDTO(x: Long, y: Long, width: Long, height: Long): List<MapDataDTO> {
    val rawData = mapDataDao.findAllInRange(x, y, width, height) ?: return emptyList()

    val dtos = ArrayList<MapDataDTO>(rawData.size)
    for (md in rawData) {
      try {
        val data = uncompress(md.data)

        ByteArrayInputStream(data).use { bis ->
          ObjectInputStream(bis).use { ois ->

            val dto = ois.readObject() as MapDataDTO
            dtos.add(dto)
          }
        }
      } catch (e: IOException) {
        LOG.error("Could not deserialize map dto.", e)
      } catch (e: ClassNotFoundException) {
        LOG.error("Could not deserialize map dto.", e)
      }
    }

    return dtos
  }

  /**
   * A list of chunk coordinates must be given and the method will return all
   * chunks of map data for the given coordinates.
   */
  fun getChunks(chunkCords: List<Point>): List<MapChunk> {
    return chunkCords.asSequence()
        .map { Pair(it, MapChunk.getWorldRect(it)) }
        .map { (chunkPoint, worldArea) ->
          val groundTiles = ArrayList<Int>((worldArea.width * worldArea.height).toInt())
          val dtos = getCoveredMapDataDTO(
              worldArea.x,
              worldArea.y,
              worldArea.width,
              worldArea.height
          )
          for (y in worldArea.origin.y until worldArea.origin.y + worldArea.height) {
            for (x in worldArea.origin.x until worldArea.origin.x + worldArea.width) {
              // Find the dto with the point inside.
              val curPos = Point(x, y)
              val gid = dtos.firstOrNull { dto -> dto.rect.collide(curPos) }?.getGroundGid(curPos.x, curPos.y)
                  ?: 0
              groundTiles.add(gid)
            }
          }
          // TODO Handle the different layers at this point.
          MapChunk(chunkPoint, groundTiles, emptyList())
        }.toList()
  }

  companion object {
    /**
     * Max sight range for the client in tiles in every direction.
     */
    private const val SIGHT_RANGE_TILES = 32

    /**
     * Returns the rect which lies inside the sight range of the position.
     *
     * @param pos
     * The position to generate the view area around.
     * @return The viewable rect.
     */
    fun getViewRect(pos: Point): Rect {
      return Rect(
          pos.x - SIGHT_RANGE_TILES,
          pos.y - SIGHT_RANGE_TILES,
          pos.x + SIGHT_RANGE_TILES,
          pos.y + SIGHT_RANGE_TILES)
    }

    /**
     * Returns the rectangular which is used for updating the clients. It is
     * usually larger than the [.getViewRect].
     *
     * @param pos
     * The position to generate the view area around.
     * @return The viewable rect.
     */
    fun getUpdateRect(pos: Point): Rect {
      return Rect(
          pos.x - SIGHT_RANGE_TILES * 2,
          pos.y - SIGHT_RANGE_TILES * 2,
          pos.x + SIGHT_RANGE_TILES * 2,
          pos.y + SIGHT_RANGE_TILES * 2)
    }

    /**
     * Checks if the chunks coordinates lie within the range reachable from the
     * given point. This is important so the client can not request chunk ids
     * not visible by it.
     *
     * @param pos
     * Current position of the player.
     * @param chunks
     * A list of chunk coordinates.
     * @return TRUE if all chunks are within reach. FALSE otherwise.
     */
    fun areChunksInClientRange(pos: Point, chunks: List<Point>): Boolean {
      // Find min max dist.
      val maxD = Math.ceil(Math.sqrt((2 * (SIGHT_RANGE_TILES * SIGHT_RANGE_TILES)).toDouble()))
      val isTooFar = chunks.asSequence().map { MapChunk.getWorldCords(it) }.any { it.getDistance(pos) > maxD }

      return !isTooFar
    }
  }
}
