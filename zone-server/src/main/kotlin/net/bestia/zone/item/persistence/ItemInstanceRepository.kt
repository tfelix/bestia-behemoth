package net.bestia.zone.item.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
interface ItemInstanceRepository : JpaRepository<ItemInstance, Long> {

  @Modifying
  @Query("update ItemInstance i set i.durability = :durability where i.id = :id")
  fun updateDurability(@Param("id") id: Long, @Param("durability") durability: Int): Int
}

fun ItemInstanceRepository.findByIdOrThrow(instanceId: Long): ItemInstance =
  findByIdOrNull(instanceId)
    ?: throw IllegalStateException("No item instance with id $instanceId")
