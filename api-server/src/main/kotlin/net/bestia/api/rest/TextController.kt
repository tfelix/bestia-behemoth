package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.api.cache.TextCache
import net.bestia.api.metrics.ApiRoute
import net.bestia.api.metrics.EndpointMetrics
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
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Texts written for the world, for the companion app and the game client. The zone sends only the id of a text;
 * the body comes from here, in the reader's language.
 */
@RestController
class TextController(
  private val zone: ZoneClient,
  private val texts: TextCache,
  private val metrics: EndpointMetrics,
) {

  /** `Content-Language` names the language of the body: English while [locale] is not written yet. */
  @GetMapping("/v1/companion/texts/{textId}/{locale}", produces = [PLAIN_UTF8])
  fun text(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable textId: String,
    @PathVariable locale: String,
  ): ResponseEntity<String> {
    if (!TEXT_ID.matches(textId) || !LOCALE.matches(locale)) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "not a text id and a locale")
    }

    val held = texts.body(textId, locale)
    if (held != null) {
      metrics.outcome(ApiRoute.TEXT, EndpointMetrics.Outcome.CACHE_HIT)
      return answer(held, locale, KEEP_FOR_GOOD)
    }

    val written = zone.ask(accountId, ZoneQuery.Text(textId, locale)).answerOrThrow<ZoneAnswer.Text>()
    metrics.outcome(ApiRoute.TEXT, EndpointMetrics.Outcome.SERVED_FROM_ZONE)
    if (written.locale != locale) {
      return answer(written.body, written.locale, ASK_AGAIN_SOON)
    }
    texts.remember(textId, locale, written.body)

    return answer(written.body, locale, KEEP_FOR_GOOD)
  }

  private fun answer(body: String, bodyLocale: String, cacheControl: CacheControl): ResponseEntity<String> {
    metrics.bodySent(ApiRoute.TEXT, body.toByteArray(StandardCharsets.UTF_8).size)

    return ResponseEntity.ok()
      .cacheControl(cacheControl)
      .header(HttpHeaders.CONTENT_LANGUAGE, bodyLocale)
      .header("X-Content-Type-Options", "nosniff")
      .body(body)
  }

  private companion object {
    const val PLAIN_UTF8 = "${MediaType.TEXT_PLAIN_VALUE};charset=UTF-8"
    val TEXT_ID = Regex("^[0-9a-f]{32}$")
    val LOCALE = Regex("^[a-z]{2}$")

    val KEEP_FOR_GOOD: CacheControl = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()

    /** A fallback body is replaced once its language is written, so no copy of it may be kept for long. */
    val ASK_AGAIN_SOON: CacheControl = CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate()
  }
}
