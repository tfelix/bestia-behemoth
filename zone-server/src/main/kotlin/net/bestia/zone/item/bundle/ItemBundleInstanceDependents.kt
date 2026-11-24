package net.bestia.zone.item.bundle

import net.bestia.zone.item.container.ItemInstanceDependents
import net.bestia.zone.item.persistence.ItemBundleRepository
import org.springframework.stereotype.Component

/** A bundle destroyed on the ground takes its contents with it, like any other destroyed item. */
@Component
class ItemBundleInstanceDependents(
  private val repository: ItemBundleRepository,
) : ItemInstanceDependents {

  override fun deleteFor(instanceIds: Collection<Long>) {
    repository.deleteAllByItemInstanceIdIn(instanceIds)
  }
}
