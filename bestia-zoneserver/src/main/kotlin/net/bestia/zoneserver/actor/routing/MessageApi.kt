package net.bestia.zoneserver.actor.routing

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.BQualifier.CLIENT_FORWARDER
import net.bestia.zoneserver.actor.BQualifier.ENTITY_FORWARDER
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
    @Qualifier(ENTITY_FORWARDER)
    private val entityForwarder: ActorRef,
    @Qualifier(CLIENT_FORWARDER)
    private val clientForwarder: ActorRef
) {

  /**
   * Only EntityEnvelopes are send towards an Entity Actor.
   */
  fun send(message: EntityMessage) {
    LOG.debug { "Sending: $message" }
    entityForwarder.tell(message, ActorRef.noSender())
  }

  fun send(message: AccountMessage) {
    LOG.debug { "Sending: $message" }
    clientForwarder.tell(message, ActorRef.noSender())
  }
}