package net.bestia.zoneserver.actor.entity.broadcast

import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import mu.KotlinLogging
import net.bestia.zoneserver.actor.ActorComponent
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * This service gathers all transmit filter and if a component has changed it looks for the defined filter
 * and will apply it in order to find the receiver(s) of the component update.
 */
@Service
class TransmitFilterService(
    transmitFilter: List<TransmitFilter>
) {

  private val transmitFilterGroupedByClass = transmitFilter
      .map { it.javaClass to it }
      .toMap()

  fun sendToReceivers(transmit: TransmitRequest, ctx: ActorRefFactory, parent: ActorRef) {
    val compFilterClass = transmit.changedComponent.javaClass.getAnnotation(ActorComponent::class.java)
    val filter = this.transmitFilterGroupedByClass[compFilterClass.transmitFilter.java]

    if (filter == null) {
      LOG.warn { "Did not find matching transmit filter for ${compFilterClass.transmitFilter}" }
      return
    }

    filter.findTransmitTargets(transmit, ctx, parent)
  }
}