package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.TileCache
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
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * A master's map, asked of the zone, which renders it from the live world and the master's charts. The rules are
 * the game client's: a 404 means nothing of this tile is charted, a fully charted tile is the same bytes for
 * everyone and immutable, a partly charted one revalidates against its ETag.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/map")
class MapController(
  private val zone: ZoneClient,
  private val tiles: TileCache,
) {

  data class MapMeta(
    val worldMapVersion: String,
    val tileSize: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val worldWidthMetres: Double,
    val worldHeightMetres: Double,
  )

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

    return MapMeta(
      info.worldMapVersion,
      info.tileSize,
      info.minLevel,
      info.maxLevel,
      info.worldWidthMetres,
      info.worldHeightMetres,
    )
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
      return png(tile, reply.body)
    }

    if (ifNoneMatch == tile.etag) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(tile.etag).cacheControl(cacheControl(tile.shared)).build()
    }

    // Not modified against the copy this service holds, so the bytes are here - unless the cache let them go
    // between the check above and now, in which case the zone is asked once more, unconditionally.
    val bytes = tiles.bytes(tile.etag)
      ?: zone.ask(accountId, ZoneQuery.MapTile(masterId, level, x, y, null)).also { it.answerOrThrow<ZoneAnswer.Tile>() }.body
    tiles.remember(masterId, path, tile.etag, bytes)
    return png(tile, bytes)
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
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(places.etag).cacheControl(cacheControl(places.shared)).build()
    }

    return ResponseEntity.ok()
      .eTag(places.etag)
      .cacheControl(cacheControl(places.shared))
      .contentType(MediaType.APPLICATION_JSON)
      .body(places.places.map { Place(it.id, it.kind, it.x, it.y, it.name, it.tier, it.poi) })
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
