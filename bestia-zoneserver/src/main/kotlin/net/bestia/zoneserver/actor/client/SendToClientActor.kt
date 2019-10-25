package net.bestia.zoneserver.actor.client

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Terminated
import com.google.common.collect.HashBiMap
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.AkkaConfiguration.Companion.CONNECTION_MANAGER
import org.springframework.beans.factory.annotation.Qualifier

private val LOG = KotlinLogging.logger { }

/**
 * This one currently looks up the client connections at the cluster singelton.
 * In the future this manager might implement a caching to reduce the load on the
 * central connection manager.
 */
@Actor
class SendToClientActor(
    @Qualifier(CONNECTION_MANAGER)
    private val clusterClientConnectionManagerActor: ActorRef
) : AbstractActor() {
  private val clientCache = HashBiMap.create<Long, ActorRef>()

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(AccountMessage::class.java, ::sendToClient)
        .match(ClientSocketResponse::class.java, ::receivedSocketResponse)
        .match(Terminated::class.java, ::socketActorTerminated)
        .build()
  }

  private fun sendToClient(msg: AccountMessage) {
    val socketActor = clientCache[msg.accountId]

    if (socketActor != null) {
      socketActor.tell(msg, self)
    } else {
      LOG.debug { "Requesting socket for account ${msg.accountId} from cluster cache" }
      val clientSocketRequest = ClientSocketRequest(msg.accountId, msg)
      clusterClientConnectionManagerActor.tell(clientSocketRequest, self)
    }
  }

  private fun receivedSocketResponse(msg: ClientSocketResponse) {
    if (msg.socketActor == null) {
      LOG.info { "Client unknown, can not deliver message '${msg.context}'" }
      return
    }

    LOG.debug { "Received socket '${msg.socketActor}' for account ${msg.accountId} from cluster cache" }

    clientCache[msg.accountId] = msg.socketActor
    context.watch(msg.socketActor)
    val contextMsg = msg.context ?: return
    msg.socketActor.tell(contextMsg, self)
  }

  private fun socketActorTerminated(msg: Terminated) {
    LOG.debug { "Watched socket terminated '${msg.actor}'" }
    clientCache.inverse().remove(msg.actor)
  }

  companion object {
    const val NAME = "nodeClientConnectionManager"
  }
}