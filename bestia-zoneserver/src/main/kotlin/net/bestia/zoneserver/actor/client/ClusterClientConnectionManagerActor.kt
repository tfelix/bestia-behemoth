package net.bestia.zoneserver.actor.client

import akka.actor.AbstractActor
import net.bestia.zoneserver.actor.Actor

/**
 * This is the cluster wide connection manager. It keeps track of all the current connection to the cluster.
 * The nodes will have their own NodeClientConnectionManager which will be queried first if there is data to
 * be send to a client. But if they get a client request they dont know they will request this connection actor
 * here.
 *
 */
class ClusterClientConnectionManagerActor : AbstractActor() {
  override fun createReceive(): Receive {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}

@Actor
class NodeClientConnectionManagerActor : AbstractActor() {
  override fun createReceive(): Receive {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}