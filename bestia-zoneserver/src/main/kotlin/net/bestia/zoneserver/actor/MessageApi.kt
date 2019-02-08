package net.bestia.zoneserver.actor

import akka.actor.ActorRef
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * This is the central interface for any external component like services or
 * components to interact with the akka system.
 *
 * @author Thomas Felix
 */
@Service
class MessageApi(
        @Qualifier("router")
        private val router: ActorRef
) {

  /**
   * Sends a message to the post master. Depending on the envelope this message is routed to
   * the appropriate receiver.
   */
  fun send(message: Any) {
    LOG.debug { "Sending: $message" }
    router.tell(message, ActorRef.noSender())
  }
}