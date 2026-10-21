package net.bestia.api.push

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class HttpWebPushSender(private val vapid: Vapid) : WebPushSender {

  private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()

  override fun send(subscription: PushSubscription, payload: ByteArray): WebPushSender.Outcome {
    val endpoint = URI.create(subscription.endpoint)
    val body = WebPushEncryption.encrypt(payload, P256.fromBase64Url(subscription.p256dh), P256.fromBase64Url(subscription.auth))

    val request = HttpRequest.newBuilder(endpoint)
      .timeout(TIMEOUT)
      .header("Authorization", vapid.authorization(endpoint))
      .header("Content-Encoding", "aes128gcm")
      .header("Content-Type", "application/octet-stream")
      .header("TTL", TIME_TO_LIVE_SECONDS)
      .header("Urgency", "normal")
      .POST(HttpRequest.BodyPublishers.ofByteArray(body))
      .build()

    return try {
      val status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
      when (status) {
        in 200..299 -> WebPushSender.Outcome.DELIVERED
        404, 410 -> WebPushSender.Outcome.GONE
        else -> {
          LOG.warn { "Push service at ${endpoint.host} answered $status" }
          WebPushSender.Outcome.FAILED
        }
      }
    } catch (e: Exception) {
      LOG.warn { "Push to ${endpoint.host} failed: ${e.message}" }
      WebPushSender.Outcome.FAILED
    }
  }

  private companion object {
    val TIMEOUT: Duration = Duration.ofSeconds(10)

    /** A notification older than a day is news nobody wants any more. */
    const val TIME_TO_LIVE_SECONDS = "86400"
    val LOG = KotlinLogging.logger { }
  }
}
