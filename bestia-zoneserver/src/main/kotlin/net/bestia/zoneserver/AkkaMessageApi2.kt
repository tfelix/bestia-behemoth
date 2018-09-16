package net.bestia.zoneserver

import akka.actor.ActorRef
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

@Service
class AkkaMessageApi2(
        @Qualifier("router")
        private val router: ActorRef
) {


  fun send(message: Any) {
    LOG.debug { "Sending: $message" }
    router.tell(message, ActorRef.noSender())
  }
}