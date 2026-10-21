package net.bestia.zone.companion.query

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.cartography.tile.MapTileService
import net.bestia.zone.cartography.tile.TileId
import net.bestia.zone.cartography.tile.TileRenderPool
import org.springframework.stereotype.Service

/**
 * The map for the companion app, from the same [MapTileService] as the game client's: the same tiles, the same
 * ETags, the same fog. A 404 means the same thing too - the master has charted none of this tile.
 */
@Service
class MapQueries(
  private val tiles: MapTileService,
  private val owned: OwnedMasters,
) {

  fun meta(correlationId: String): BusReply {
    val meta = tiles.meta()

    return BusReply.ok(
      correlationId,
      ZoneAnswer.MapInfo(
        worldMapVersion = meta.getValue("worldMapVersion") as String,
        tileSize = meta.getValue("tileSize") as Int,
        minLevel = meta.getValue("minLevel") as Int,
        maxLevel = meta.getValue("maxLevel") as Int,
        worldWidthMetres = meta.getValue("worldWidthMetres") as Double,
        worldHeightMetres = meta.getValue("worldHeightMetres") as Double,
      )
    )
  }

  fun tile(correlationId: String, accountId: Long, query: ZoneQuery.MapTile): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    val tile = try {
      tiles.tile(query.masterId, TileId(query.level, query.x, query.y))
    } catch (e: IllegalArgumentException) {
      return BusReply.of(correlationId, Status.INVALID, e.message)
    } catch (_: TileRenderPool.Unavailable) {
      return BusReply.of(correlationId, Status.BUSY)
    } ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    val answer = ZoneAnswer.Tile(tile.etag, tile.shared)
    if (query.ifNoneMatch == tile.etag) {
      return BusReply(correlationId, Status.NOT_MODIFIED, answer)
    }
    return BusReply.ok(correlationId, answer, tile.bytes)
  }

  fun places(correlationId: String, accountId: Long, query: ZoneQuery.MapPlaces): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    val places = try {
      tiles.places(query.masterId, TileId(query.level, query.x, query.y))
    } catch (e: IllegalArgumentException) {
      return BusReply.of(correlationId, Status.INVALID, e.message)
    } ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    if (query.ifNoneMatch == places.etag) {
      return BusReply(correlationId, Status.NOT_MODIFIED, ZoneAnswer.Places(places.etag, places.shared, emptyList()))
    }

    val listed = places.places.map { ZoneAnswer.Places.Place(it.id, it.kind, it.x, it.y, it.name, it.tier, it.poi) }
    return BusReply.ok(correlationId, ZoneAnswer.Places(places.etag, places.shared, listed))
  }
}
