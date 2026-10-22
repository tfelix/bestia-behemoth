package net.bestia.zone.companion.query

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.cartography.tile.MapTileService
import net.bestia.zone.compendium.CompendiumStore
import net.bestia.zone.cartography.tile.TileId
import net.bestia.zone.cartography.tile.TileRenderPool
import org.springframework.stereotype.Service

/**
 * The map, for the companion app and the game client alike: tiles, the places on them and the services in them,
 * all masked to what the master has charted. A NOT_FOUND means the master has charted none of this tile - and,
 * indistinguishably, that the master is not the asker's.
 *
 * An uncharted tile is never rendered, so nothing about that ground leaves the zone: not its imagery, and not
 * the size of a file that would hint at what is drawn on it.
 */
@Service
class MapQueries(
  private val tiles: MapTileService,
  private val owned: OwnedMasters,
  private val compendium: CompendiumStore,
  private val catalogue: BestiaCatalogue,
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
        metresPerVoxel = meta.getValue("metresPerVoxel") as Double,
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

  /** A NOT_FOUND too when the master has not seen the species: its habitat is part of what seeing one teaches. */
  fun habitat(correlationId: String, accountId: Long, query: ZoneQuery.HabitatTile): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    if (!compendium.hasSeen(query.masterId, query.speciesId)) return BusReply.of(correlationId, Status.NOT_FOUND)
    val species = catalogue.find(query.speciesId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    val tile = try {
      tiles.habitat(query.masterId, species, TileId(query.level, query.x, query.y))
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

  /** Never cached for keeps: a service can be destroyed, so its ETag carries the world's service revision. */
  fun services(correlationId: String, accountId: Long, query: ZoneQuery.MapServices): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    val services = try {
      tiles.services(query.masterId, TileId(query.level, query.x, query.y))
    } catch (e: IllegalArgumentException) {
      return BusReply.of(correlationId, Status.INVALID, e.message)
    } ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    if (query.ifNoneMatch == services.etag) {
      return BusReply(correlationId, Status.NOT_MODIFIED, ZoneAnswer.Services(services.etag, emptyList()))
    }

    val listed = services.services.map {
      ZoneAnswer.Services.Service(it.id, it.kind.name, it.business, it.x, it.y, it.settlement, it.openFrom, it.openTo)
    }
    return BusReply.ok(correlationId, ZoneAnswer.Services(services.etag, listed))
  }
}
