package net.bestia.zone.item.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ItemBundleRepository : JpaRepository<ItemBundle, Long> {

  fun findByItemInstanceId(itemInstanceId: Long): ItemBundle?

  fun deleteAllByItemInstanceIdIn(itemInstanceIds: Collection<Long>)
}
