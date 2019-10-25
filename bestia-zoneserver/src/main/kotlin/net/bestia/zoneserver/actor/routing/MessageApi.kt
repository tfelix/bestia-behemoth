package net.bestia.zoneserver.actor.routing

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.AkkaConfiguration
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * This is the central interface for any external component like services or
 * components to interact with the entity system.
 *
 * @author Thomas Felix
 */
@Service
class MessageApi(
    @Qualifier(AkkaConfiguration.ENTITY_FORWARDER_QUALIFIER)
    private val entityForwarder: ActorRef,
    @Qualifier(AkkaConfiguration.CLIENT_FORWARDER_QUALIFIER)
    private val clientForwarder: ActorRef
) {

  fun send(message: EntityEnvelope) {
    LOG.debug { "Sending: $message" }
    entityForwarder.tell(message, ActorRef.noSender())
  }

  fun send(message: ClientEnvelope) {
    LOG.debug { "Sending: $message" }
    clientForwarder.tell(message, ActorRef.noSender())
  }
}