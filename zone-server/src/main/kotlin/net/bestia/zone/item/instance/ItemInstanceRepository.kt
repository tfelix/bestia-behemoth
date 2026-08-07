package net.bestia.zone.item.instance

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ItemInstanceRepository : JpaRepository<ItemInstance, Long> {

  /**
   * Forgets who forged the given master's crafted items, so the master row can be deleted while the items
   * themselves live on in whoever's hands they ended up in.
   *
   * A bulk update rather than a load-and-set because [ItemInstance.craftedBy] is a `val` - there is no
   * setter to null it through - and because this only clears one column, so there is no cascade for the
   * bulk statement to bypass.
   */
  @Modifying(flushAutomatically = true)
  @Query("update ItemInstance i set i.craftedBy = null where i.craftedBy.id = :masterId")
  fun clearCraftedByMaster(@Param("masterId") masterId: Long): Int
}

fun ItemInstanceRepository.findByIdOrThrow(instanceId: Long): ItemInstance =
  findByIdOrNull(instanceId)
    ?: throw IllegalStateException("No item instance with id $instanceId")
