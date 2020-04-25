package net.bestia.zoneserver.actor.entity.broadcast

import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import org.springframework.stereotype.Component

/**
 * Needed as default parameter for the [ActorComponent] annotation.
 */
@Component
class NoOpTransmitFilter : TransmitFilter {
  override fun findTransmitTargets(transmit: TransmitRequest, ctx: ActorRefFactory, parent: ActorRef) {
    // no op
  }
}