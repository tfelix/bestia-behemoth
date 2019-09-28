package net.bestia.zoneserver.actor.routing

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.zoneserver.actor.AkkaConfiguration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * This is the central interface for any external component like services or
 * components to interact with the system router.
 *
 * @author Thomas Felix
 */
@Service
class SystemMessageService(
    @Qualifier(AkkaConfiguration.SYSTEM_ROUTER_QUALIFIER)
    private val router: ActorRef
) {

  fun send(message: Any) {
    LOG.debug { "Sending: $message" }
    router.tell(message, ActorRef.noSender())
  }
}