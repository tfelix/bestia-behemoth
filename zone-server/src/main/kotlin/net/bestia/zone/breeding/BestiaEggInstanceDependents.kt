package net.bestia.zone.breeding

import net.bestia.zone.breeding.persistence.BestiaEggRepository
import net.bestia.zone.item.container.ItemInstanceDependents
import org.springframework.stereotype.Component

/** An egg's row is keyed by its item instance, so it goes before the instance. */
@Component
class BestiaEggInstanceDependents(
  private val eggs: BestiaEggRepository,
) : ItemInstanceDependents {

  override fun deleteFor(instanceIds: Collection<Long>) {
    eggs.deleteAllByItemInstanceIdIn(instanceIds)
  }
}
