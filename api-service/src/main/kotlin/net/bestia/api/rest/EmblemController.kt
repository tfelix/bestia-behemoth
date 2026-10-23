package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.EmblemCache
import net.bestia.api.metrics.ApiRoute
import net.bestia.api.metrics.EndpointMetrics
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

/**
 * Guild emblems, for the companion app and the game client. Named by the hash of the PNG, so a new emblem is a new
 * URL and every copy - here, in a browser, on the game client's disk - is kept for good.
 */
@RestController
class EmblemController(
  private val zone: ZoneClient,
  private val emblems: EmblemCache,
  private val metrics: EndpointMetrics,
) {

  @GetMapping("/v1/companion/emblems/{hash}.png")
  fun emblem(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable hash: String,
  ): ResponseEntity<ByteArray> {
    if (!HASH.matches(hash)) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "not an emblem hash")
    }

    val held = emblems.png(hash)
    val png = held ?: fetch(accountId, hash)
    metrics.outcome(ApiRoute.EMBLEM, if (held != null) EndpointMetrics.Outcome.CACHE_HIT else EndpointMetrics.Outcome.SERVED_FROM_ZONE)
    metrics.bodySent(ApiRoute.EMBLEM, png.size)

    return ResponseEntity.ok()
      .cacheControl(CacheControl.maxAge(Duration.ofDays(IMMUTABLE_DAYS)).cachePublic().immutable())
      .contentType(MediaType.IMAGE_PNG)
      // The zone stores only pixels it re-encoded, but a browser must never guess anything else from them.
      .header("X-Content-Type-Options", "nosniff")
      .body(png)
  }

  private fun fetch(accountId: Long, hash: String): ByteArray {
    val reply = zone.ask(accountId, ZoneQuery.GuildEmblem(hash))
    reply.answerOrThrow<ZoneAnswer.Emblem>()
    emblems.remember(hash, reply.body)

    return reply.body
  }

  private companion object {
    const val IMMUTABLE_DAYS = 365L
    val HASH = Regex("^[0-9a-f]{64}$")
  }
}
