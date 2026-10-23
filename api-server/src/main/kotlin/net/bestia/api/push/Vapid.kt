package net.bestia.api.push

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.net.URI
import java.security.Signature
import java.time.Duration
import java.time.Instant

/**
 * VAPID (RFC 8292): proves to a push service that a message comes from the server the browser subscribed to.
 * Every request carries a short JWT for the push service's origin, signed ES256 with the VAPID key.
 */
@Component
class Vapid(private val properties: PushProperties) {

  private val privateKey = P256.privateKey(P256.fromBase64Url(properties.vapidPrivateKey))
  private val mapper = jacksonObjectMapper()

  /** The `Authorization` header value for a message to [endpoint]. */
  fun authorization(endpoint: URI, now: Instant = Instant.now()): String {
    val audience = "${endpoint.scheme}://${endpoint.rawAuthority}"
    val header = mapOf("typ" to "JWT", "alg" to "ES256")
    val claims = mapOf("aud" to audience, "exp" to now.plus(LIFETIME).epochSecond, "sub" to properties.subject)

    val signingInput = encode(header) + "." + encode(claims)
    val signature = Signature.getInstance("SHA256withECDSAinP1363Format").run {
      initSign(privateKey)
      update(signingInput.toByteArray())
      sign()
    }

    return "vapid t=$signingInput.${P256.base64Url(signature)}, k=${properties.vapidPublicKey}"
  }

  private fun encode(part: Map<String, Any>): String {
    return P256.base64Url(mapper.writeValueAsBytes(part))
  }

  private companion object {
    /** Push services refuse a token valid for more than a day; half that leaves room for clock skew. */
    val LIFETIME: Duration = Duration.ofHours(12)
  }
}
