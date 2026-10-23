package net.bestia.api

import net.bestia.api.push.PushSubscription
import net.bestia.api.push.WebPushSender
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Records each push instead of sending it. Its own class, not nested in a test: Spring keys a context on the nested
 * configuration of the test class too, so a nested one gave that class a second context on the same bus.
 */
@TestConfiguration(proxyBeanMethods = false)
class RecordingPush {
  val sent = CopyOnWriteArrayList<Pair<Long, String>>()

  @Bean
  @Primary
  fun recordingSender(): WebPushSender {
    return WebPushSender { subscription: PushSubscription, payload: ByteArray ->
      sent += subscription.accountId to String(payload)
      WebPushSender.Outcome.DELIVERED
    }
  }
}
