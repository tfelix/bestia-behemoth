package net.bestia.api.push

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import java.net.URI
import java.security.Signature
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VapidTest {

  private val properties = PushProperties(
    vapidPublicKey = "BPC8EVmzvqSQvPRBDi7FjAQqyAJGZsxH9jKT16aTDbUK79Vg4h1rwiPXhadB5_X6i1fOj0--Na1QGZJdQgNlfko",
    vapidPrivateKey = "9LkI9jzU2xuenKlS-a1-YjSER1TZ8cJ77fLoehGzHZw",
    subject = "mailto:admin@bestia.game",
  )

  @Test
  fun `the token is for the push service's origin and verifies against the public key`() {
    val now = Instant.ofEpochSecond(1_800_000_000)

    val header = Vapid(properties).authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc"), now)

    val token = header.removePrefix("vapid t=").substringBefore(", k=")
    assertEquals(properties.vapidPublicKey, header.substringAfter(", k="))

    val (head, body, signature) = token.split('.')
    val claims = jacksonObjectMapper().readValue<Map<String, Any>>(P256.fromBase64Url(body))
    assertEquals("https://fcm.googleapis.com", claims["aud"])
    assertEquals(now.epochSecond + 12 * 3600, (claims["exp"] as Number).toLong())

    val verified = Signature.getInstance("SHA256withECDSAinP1363Format").run {
      initVerify(P256.publicKey(P256.fromBase64Url(properties.vapidPublicKey)))
      update("$head.$body".toByteArray())
      verify(P256.fromBase64Url(signature))
    }
    assertTrue(verified)
  }
}
