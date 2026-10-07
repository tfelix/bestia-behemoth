package net.bestia.zone.item.container

import net.bestia.zone.item.persistence.ItemInstanceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Deletes an instance that sits in no container, such as one destroyed on the ground. */
@Service
class LooseInstanceDisposal(
  private val itemInstanceRepository: ItemInstanceRepository,
  private val dependents: List<ItemInstanceDependents>,
) {

  @Transactional
  fun destroy(instanceId: Long) {
    dependents.forEach { it.deleteFor(listOf(instanceId)) }
    itemInstanceRepository.deleteById(instanceId)
  }
}
