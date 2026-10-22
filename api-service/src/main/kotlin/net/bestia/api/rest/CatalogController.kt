package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

/**
 * The items and species the app's ids refer to. Identifiers only: names are translated in the app, from the same
 * tables the game client uses. Fixed until the zone's next boot, so cached here and revalidated by version.
 */
@RestController
@RequestMapping("/v1/companion/catalog")
class CatalogController(private val zone: ZoneClient) {

  data class Item(
    val id: Long,
    val identifier: String,
    val type: String,
    val weight: Int,
    val equipSlot: String?,
    val stackable: Boolean,
  )

  /** [habitat] holds biome names, empty for any land; [inCompendium] is false for species the compendium leaves out. */
  data class Species(
    val id: Long,
    val identifier: String,
    val kind: String,
    val element: String,
    val level: Int,
    val habitat: List<String>,
    val inCompendium: Boolean,
  )

  data class Catalog(val version: String, val items: List<Item>, val bestias: List<Species>)

  @Volatile
  private var cached: Pair<Instant, Catalog>? = null

  @GetMapping
  fun catalog(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) ifNoneMatch: String?,
  ): ResponseEntity<Catalog> {
    val catalog = current(accountId)
    val etag = "\"${catalog.version}\""

    if (ifNoneMatch == etag) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build()
    }
    return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache()).body(catalog)
  }

  private fun current(accountId: Long): Catalog {
    val held = cached
    if (held != null && held.first.plus(TIME_TO_LIVE).isAfter(Instant.now())) {
      return held.second
    }

    val data = zone.ask(accountId, ZoneQuery.Catalog).answerOrThrow<ZoneAnswer.CatalogData>()
    val catalog = Catalog(
      data.version,
      data.items.map { Item(it.id, it.identifier, it.type, it.weight, it.equipSlot, it.stackable) },
      data.bestias.map { Species(it.id, it.identifier, it.kind, it.element, it.level, it.habitat, it.inCompendium) },
    )
    cached = Instant.now() to catalog
    return catalog
  }

  private companion object {
    val TIME_TO_LIVE: Duration = Duration.ofMinutes(10)
  }
}
