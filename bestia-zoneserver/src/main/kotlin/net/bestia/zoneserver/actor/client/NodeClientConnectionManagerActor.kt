package net.bestia.zoneserver.actor.client

import akka.actor.AbstractActor
import akka.actor.ActorRef
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.AkkaConfiguration.Companion.CONNECTION_MANAGER
import org.springframework.beans.factory.annotation.Qualifier

/**
 * This one currently looks up the client connections at the cluster singelton.
 * In the future this manager might implement a caching to reduce the load on the
 * central connection manager.
 */
@Actor
class NodeClientConnectionManagerActor(
    @Qualifier(CONNECTION_MANAGER)
    private val clusterClientConnectionManagerActor: ActorRef
) : AbstractActor() {
  override fun createReceive(): Receive {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}