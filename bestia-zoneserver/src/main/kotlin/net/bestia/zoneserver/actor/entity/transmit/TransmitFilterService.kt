package net.bestia.zoneserver.actor.entity.transmit

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
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

  private fun getFilter(transmit: TransmitRequest): TransmitFilter? {
    val compFilterClass = transmit.changedComponent.javaClass.getAnnotation(ClientTransmitFilter::class.java)
        ?: return null
    LOG.trace { "Looking for transmit filter ${compFilterClass.value} on component '${transmit.changedComponent.javaClass.simpleName}'" }

    return transmitFilterGroupedByClass[compFilterClass.value.java]
  }

  fun findTransmitCandidates(transmit: TransmitRequest): Set<Long> {
    val filter = getFilter(transmit)
        ?: return emptySet()

    val candidates = filter.findTransmitCandidates(transmit)

    LOG.trace { "Found transmit candidates entity ids: $candidates" }

    return candidates
  }

  fun selectTransmitCandidates(candidates: Set<Entity>, transmit: TransmitRequest): Set<Long> {
    LOG.trace { "Selecting transmit candidates: $candidates" }

    val filter = getFilter(transmit)

    if (filter == null) {
      LOG.warn { "No transmit filter found for request: I$transmit" }
      return emptySet()
    }

    val candidates = filter.selectTransmitTargetAccountIds(candidates, transmit)

    LOG.debug { "Transmit: $transmit, candidates: $candidates" }

    return candidates
  }
}