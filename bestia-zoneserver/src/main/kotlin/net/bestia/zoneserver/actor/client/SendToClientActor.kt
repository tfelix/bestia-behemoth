package net.bestia.zoneserver.actor.client

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.zoneserver.actor.Actor

private val LOG = KotlinLogging.logger { }

/**
 * This actor sends the incoming message towards the registered cluster sharding
 * actor which holds the connection to a client.
 *
 * @author Thomas Felix
 */
@Actor
class SendToClientActor : AbstractActor() {

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .build()
  }

  companion object {
    const val NAME = "sendToClient"
  }
}
