package net.bestia.zoneserver.actor.entity.transmit

import akka.actor.ActorRef
import akka.actor.ActorRefFactory

/**
 * This filter determines if a component should be transmitted to one
 * or multiple clients.
 */
interface TransmitFilter {
  /**
   *
   * @return Client account ids which should receive the component update.
   */
  fun findTransmitTargets(transmit: TransmitRequest, ctx: ActorRefFactory, parent: ActorRef)
}

