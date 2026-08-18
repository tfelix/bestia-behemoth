package net.bestia.zone.cartography.web

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import net.bestia.zone.cartography.tile.MapTileService
import net.bestia.zone.cartography.tile.TileId
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

/**
 * The map's HTTP surface: geometry, and tiles.
 *
 * ### Why HTTP rather than the game socket
 *
 * A screen of map is a few hundred kilobytes of PNG, and the client wants to re-ask for the same tiles every time
 * it opens the window. Both of those are things HTTP already solves and a message envelope does not: conditional
 * requests, a cache that survives a restart, and a transfer that cannot delay a movement packet behind it.
 *
 * ### Caching, and the two kinds of tile
 *
 * A fully charted tile is the same bytes for every player, so it goes out `public` and `immutable` under a strong
 * ETag - the world key is in the ETag, so a regenerated world or a restyled map invalidates every stored copy
 * without anyone having to remember to. A partly charted tile is `private` and revalidated, because its content
 * depends on how much of that ground the asker has surveyed; its ETag carries the coverage digest, so a client
 * whose charts have not changed still gets a 304.
 *
 * The client is not told when its charts change. It finds out from its own inventory - a chart is an item, and
 * the inventory already syncs - and drops its tile cache when the set of charts it holds changes. That is why
 * there is no map channel message and no coverage-changed push: the one fact the client needs is one it already
 * receives.
 */
@RestController
@RequestMapping("/map/v1")
class MapTileController(
  private val tiles: MapTileService,
) {

  /** What the client needs before it can address a tile: world size, the level range, and the cache key. */
  @GetMapping("/meta")
  fun meta(): Map<String, Any> = tiles.meta()

  /**
   * One tile, masked to what the asking master has charted.
   *
   * A **404 means "you have not charted any of this"**, and is the answer for the great majority of the world.
   * That is deliberate: an uncharted tile is never rendered, so nothing about that ground reaches the client -
   * not the imagery, and not the file size that would hint at what is drawn on it.
   */
  @GetMapping("/t/{level}/{tx}/{ty}.png")
  fun tile(
    @PathVariable level: Int,
    @PathVariable tx: Long,
    @PathVariable ty: Long,
    request: HttpServletRequest
  ): ResponseEntity<ByteArray> {
    val masterId = request.getAttribute(MapAuthFilter.MASTER_ID) as? Long
      ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No master on the request")

    val id = try {
      TileId(level, tx, ty)
    } catch (e: IllegalArgumentException) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    }

    val tile = try {
      tiles.tile(masterId, id)
    } catch (e: IllegalArgumentException) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
    } ?: return ResponseEntity.notFound().build()

    // Compared here rather than by a Spring `ShallowEtagHeaderFilter`, which computes the tag from the body it
    // has already produced - the whole value of the tag for a masked tile is skipping the render.
    if (request.getHeader("If-None-Match") == tile.etag) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(tile.etag).build()
    }

    val cacheControl = if (tile.shared) {
      CacheControl.maxAge(Duration.ofDays(IMMUTABLE_DAYS)).cachePublic().immutable()
    } else {
      // Revalidate every time: the bytes change when the player charts more of this tile, and the ETag is what
      // makes that cheap.
      CacheControl.noCache().cachePrivate()
    }

    return ResponseEntity.ok()
      .eTag(tile.etag)
      .cacheControl(cacheControl)
      .contentType(MediaType.IMAGE_PNG)
      .body(tile.bytes)
  }

  private companion object {

    /** A year, which is the conventional ceiling and what `immutable` means in practice. */
    const val IMMUTABLE_DAYS = 365L

    private val LOG = KotlinLogging.logger { }
  }
}
