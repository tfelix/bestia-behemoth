package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.TileCache
import net.bestia.api.metrics.ApiRoute
import net.bestia.api.metrics.EndpointMetrics
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * A master's map, for the companion app and the game client: asked of the zone, which renders it from the live
 * world and the master's charts. A 404 means nothing of this tile is charted; a fully charted tile is the same
 * bytes for everyone and immutable; a partly charted one revalidates against its ETag.
 *
 * Tiles and places end in `.png` and `.json` because the game client stores them as files; services have no
 * extension, because they are a live answer kept in memory and revalidated.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/map")
class MapController(
  private val zone: ZoneClient,
  private val tiles: TileCache,
  private val metrics: EndpointMetrics,
) {

  data class MapMeta(
    val worldMapVersion: String,
    val tileSize: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val worldWidthMetres: Double,
    val worldHeightMetres: Double,
    val metresPerVoxel: Double,
  )

  /** [openFrom] and [openTo] are game hours, null for something with no hours. */
  data class Service(
    val id: Long,
    val kind: String,
    val business: String?,
    val x: Double,
    val y: Double,
    val settlement: String?,
    val openFrom: Int?,
    val openTo: Int?,
  )

  data class Services(val services: List<Service>)

  data class Place(
    val id: Long,
    val kind: String,
    val x: Double,
    val y: Double,
    val name: String?,
    val tier: String?,
    val poi: String?,
  )

  @GetMapping("/meta")
  fun meta(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): MapMeta {
    val info = zone.ask(accountId, ZoneQuery.MapMeta).answerOrThrow<ZoneAnswer.MapInfo>()
    metrics.outcome(ApiRoute.MAP_META, EndpointMetrics.Outcome.SERVED_FROM_ZONE)

    return MapMeta(
      info.worldMapVersion,
      info.tileSize,
      info.minLevel,
      info.maxLevel,
      info.worldWidthMetres,
      info.worldHeightMetres,
      info.metresPerVoxel,
    )
  }

  /** Always `private, no-cache`: a service can be knocked down, and the ETag names the world's state. */
  @GetMapping("/s/{level}/{x}/{y}")
  fun services(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable level: Int,
    @PathVariable x: Long,
    @PathVariable y: Long,
    @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
  ): ResponseEntity<Services> {
    val reply = zone.ask(accountId, ZoneQuery.MapServices(masterId, level, x, y, ifNoneMatch))
    val services = reply.answerOrThrow<ZoneAnswer.Services>()
    val revalidate = CacheControl.noCache().cachePrivate()

    if (reply.status == BusReply.Status.NOT_MODIFIED) {
      metrics.outcome(ApiRoute.MAP_SERVICES, EndpointMetrics.Outcome.CLIENT_304)
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(services.etag).cacheControl(revalidate).build()
    }

    metrics.outcome(ApiRoute.MAP_SERVICES, EndpointMetrics.Outcome.SERVED_FROM_ZONE)
    val listed = services.services.map {
      Service(it.id, it.kind, it.business, it.x, it.y, it.settlement, it.openFrom, it.openTo)
    }
    return ResponseEntity.ok().eTag(services.etag).cacheControl(revalidate).body(Services(listed))
  }

  @GetMapping("/t/{level}/{x}/{y}.png")
  fun tile(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable level: Int,
    @PathVariable x: Long,
    @PathVariable y: Long,
    @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
  ): ResponseEntity<ByteArray> {
    val path = "$level/$x/$y"
    val held = tiles.knownEtag(masterId, path)?.takeIf { tiles.bytes(it) != null }
    val reply = zone.ask(accountId, ZoneQuery.MapTile(masterId, level, x, y, ifNoneMatch ?: held))
    val tile = reply.answerOrThrow<ZoneAnswer.Tile>()

    if (reply.status == BusReply.Status.OK) {
      tiles.remember(masterId, path, tile.etag, reply.body)
      metrics.outcome(ApiRoute.MAP_TILE, EndpointMetrics.Outcome.SERVED_FROM_ZONE)
      metrics.bodySent(ApiRoute.MAP_TILE, reply.body.size)
      return png(tile, reply.body)
    }

    if (ifNoneMatch == tile.etag) {
      metrics.outcome(ApiRoute.MAP_TILE, EndpointMetrics.Outcome.CLIENT_304)
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(tile.etag).cacheControl(cacheControl(tile.shared)).build()
    }

    // Not modified against the copy this service holds, so the bytes are here - unless the cache let them go
    // between the check above and now, in which case the zone is asked once more, unconditionally.
    val cached = tiles.bytes(tile.etag)
    val bytes = cached
      ?: zone.ask(accountId, ZoneQuery.MapTile(masterId, level, x, y, null)).also { it.answerOrThrow<ZoneAnswer.Tile>() }.body
    metrics.outcome(ApiRoute.MAP_TILE, if (cached != null) EndpointMetrics.Outcome.CACHE_REVALIDATED else EndpointMetrics.Outcome.EVICTED_REFETCH)
    metrics.bodySent(ApiRoute.MAP_TILE, bytes.size)
    tiles.remember(masterId, path, tile.etag, bytes)
    return png(tile, bytes)
  }

  /**
   * Where a species the master has seen lives, to lay over the tile of the same path. Never cached publicly: who may
   * see it depends on the master's compendium, not only on the charts.
   */
  @GetMapping("/h/{speciesId}/{level}/{x}/{y}.png")
  fun habitat(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable speciesId: Long,
    @PathVariable level: Int,
    @PathVariable x: Long,
    @PathVariable y: Long,
    @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
  ): ResponseEntity<ByteArray> {
    val reply = zone.ask(accountId, ZoneQuery.HabitatTile(masterId, speciesId, level, x, y, ifNoneMatch))
    val tile = reply.answerOrThrow<ZoneAnswer.Tile>()
    val revalidate = CacheControl.noCache().cachePrivate()

    if (reply.status == BusReply.Status.NOT_MODIFIED) {
      metrics.outcome(ApiRoute.MAP_HABITAT, EndpointMetrics.Outcome.CLIENT_304)
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(tile.etag).cacheControl(revalidate).build()
    }

    metrics.outcome(ApiRoute.MAP_HABITAT, EndpointMetrics.Outcome.SERVED_FROM_ZONE)
    metrics.bodySent(ApiRoute.MAP_HABITAT, reply.body.size)
    return ResponseEntity.ok().eTag(tile.etag).cacheControl(revalidate).contentType(MediaType.IMAGE_PNG).body(reply.body)
  }

  @GetMapping("/p/{level}/{x}/{y}.json")
  fun places(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable level: Int,
    @PathVariable x: Long,
    @PathVariable y: Long,
    @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
  ): ResponseEntity<List<Place>> {
    val reply = zone.ask(accountId, ZoneQuery.MapPlaces(masterId, level, x, y, ifNoneMatch))
    val places = reply.answerOrThrow<ZoneAnswer.Places>()

    if (reply.status == BusReply.Status.NOT_MODIFIED) {
      metrics.outcome(ApiRoute.MAP_PLACES, EndpointMetrics.Outcome.CLIENT_304)
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(places.etag).cacheControl(cacheControl(places.shared)).build()
    }

    metrics.outcome(ApiRoute.MAP_PLACES, EndpointMetrics.Outcome.SERVED_FROM_ZONE)
    return ResponseEntity.ok()
      .eTag(places.etag)
      .cacheControl(cacheControl(places.shared))
      .contentType(MediaType.APPLICATION_JSON)
      .body(places.places.map { Place(it.id, it.kind, it.x, it.y, it.name, it.tier, it.poi) })
  }

  /** Charted places whose names contain [q], best first, for the map's search box. */
  @GetMapping("/find")
  fun find(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @RequestParam q: String,
  ): List<Place> {
    val found = zone.ask(accountId, ZoneQuery.MapFind(masterId, q)).answerOrThrow<ZoneAnswer.Found>()
    return found.places.map { Place(it.id, it.kind, it.x, it.y, it.name, it.tier, it.poi) }
  }

  private fun png(tile: ZoneAnswer.Tile, bytes: ByteArray): ResponseEntity<ByteArray> {
    return ResponseEntity.ok()
      .eTag(tile.etag)
      .cacheControl(cacheControl(tile.shared))
      .contentType(MediaType.IMAGE_PNG)
      .body(bytes)
  }

  /** As the zone's own tile endpoint: shared tiles never change under their key, masked ones always revalidate. */
  private fun cacheControl(shared: Boolean): CacheControl {
    if (shared) {
      return CacheControl.maxAge(Duration.ofDays(IMMUTABLE_DAYS)).cachePublic().immutable()
    }
    return CacheControl.noCache().cachePrivate()
  }

  private companion object {
    const val IMMUTABLE_DAYS = 365L
  }
}
