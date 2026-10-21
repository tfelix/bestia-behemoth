package net.bestia.api.push

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Where an account's notifications go, and which ones it wants. */
@RestController
@RequestMapping("/v1/companion/push")
class PushController(
  private val properties: PushProperties,
  private val subscriptions: PushSubscriptionRepository,
  private val preferences: PushPreferenceRepository,
  private val notifier: PushNotifier,
) {

  data class PublicKey(val publicKey: String)

  /** The browser's `PushSubscription.toJSON()`. */
  data class Subscription(val endpoint: String, val keys: Keys) {
    data class Keys(val p256dh: String, val auth: String)
  }

  data class Unsubscribe(val endpoint: String)

  /** Quiet hours as `HH:mm` in [timeZone]; both null for none. */
  data class Preferences(
    val homeFell: Boolean,
    val bestiaDied: Boolean,
    val constructionDone: Boolean,
    val quietFrom: String?,
    val quietTo: String?,
    val timeZone: String,
  )

  /** What a page needs to call `pushManager.subscribe`. Public: it is the same for everybody. */
  @GetMapping("/key")
  fun key(): PublicKey {
    return PublicKey(properties.vapidPublicKey)
  }

  @PostMapping("/subscriptions")
  @Transactional
  fun subscribe(@RequestAttribute(ACCOUNT_ID) accountId: Long, @RequestBody body: Subscription): ResponseEntity<Void> {
    requireAllowedEndpoint(body.endpoint)
    requireKeys(body.keys)

    val hash = hash(body.endpoint)
    // A browser handed to someone else and signed in again keeps its endpoint; it now belongs to the new account.
    subscriptions.findByEndpointHash(hash)?.let {
      subscriptions.delete(it)
      subscriptions.flush()
    }
    subscriptions.save(PushSubscription(accountId, body.endpoint, hash, body.keys.p256dh, body.keys.auth))

    return ResponseEntity.status(HttpStatus.CREATED).build()
  }

  @DeleteMapping("/subscriptions")
  @Transactional
  fun unsubscribe(@RequestAttribute(ACCOUNT_ID) accountId: Long, @RequestBody body: Unsubscribe): ResponseEntity<Void> {
    subscriptions.findByEndpointHash(hash(body.endpoint))?.takeIf { it.accountId == accountId }?.let { subscriptions.delete(it) }

    return ResponseEntity.noContent().build()
  }

  @GetMapping("/preferences")
  @Transactional(readOnly = true)
  fun preferences(@RequestAttribute(ACCOUNT_ID) accountId: Long): Preferences {
    val stored = preferences.findById(accountId).orElse(null) ?: PushPreference.defaults(accountId)

    return Preferences(
      stored.homeFell,
      stored.bestiaDied,
      stored.constructionDone,
      stored.quietFrom?.let { format(it) },
      stored.quietTo?.let { format(it) },
      stored.timeZone,
    )
  }

  @PutMapping("/preferences")
  @Transactional
  fun setPreferences(@RequestAttribute(ACCOUNT_ID) accountId: Long, @RequestBody body: Preferences): Preferences {
    val zone = runCatching { ZoneId.of(body.timeZone) }.getOrNull()
      ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown time zone '${body.timeZone}'")
    if ((body.quietFrom == null) != (body.quietTo == null)) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Quiet hours need both ends")
    }

    val stored = preferences.findById(accountId).orElse(null) ?: PushPreference.defaults(accountId)
    stored.homeFell = body.homeFell
    stored.bestiaDied = body.bestiaDied
    stored.constructionDone = body.constructionDone
    stored.quietFrom = body.quietFrom?.let { parse(it) }
    stored.quietTo = body.quietTo?.let { parse(it) }
    stored.timeZone = zone.id
    stored.updatedAt = LocalDateTime.now()
    preferences.save(stored)

    return preferences(accountId)
  }

  /** Sends a notification to this account's own devices now, ignoring its preferences - to check it arrives. */
  @PostMapping("/test")
  fun test(@RequestAttribute(ACCOUNT_ID) accountId: Long): ResponseEntity<Void> {
    notifier.sendTest(accountId)
    return ResponseEntity.accepted().build()
  }

  private fun requireAllowedEndpoint(endpoint: String) {
    val uri = runCatching { URI.create(endpoint) }.getOrNull()
    val host = uri?.host?.lowercase()
    val allowed = uri?.scheme == "https" && host != null &&
      properties.allowedEndpointHosts.any { host == it || host.endsWith(".$it") }

    if (!allowed || endpoint.length > MAX_ENDPOINT_LENGTH) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a push service this server sends to")
    }
  }

  private fun requireKeys(keys: Subscription.Keys) {
    val valid = runCatching {
      P256.publicKey(P256.fromBase64Url(keys.p256dh))
      P256.fromBase64Url(keys.auth).size == AUTH_SECRET_BYTES
    }.getOrDefault(false)

    if (!valid) {
      throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The subscription's keys are not usable")
    }
  }

  private fun hash(endpoint: String): String {
    return MessageDigest.getInstance("SHA-256").digest(endpoint.toByteArray()).joinToString("") { "%02x".format(it) }
  }

  private fun format(minutes: Short): String {
    return "%02d:%02d".format(minutes / 60, minutes % 60)
  }

  private fun parse(time: String): Short {
    val parsed = runCatching { LocalTime.parse(time) }.getOrNull()
      ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a time of day: '$time'")
    return PushPreference.minutesOf(parsed)
  }

  private companion object {
    const val MAX_ENDPOINT_LENGTH = 1024
    const val AUTH_SECRET_BYTES = 16
  }
}
