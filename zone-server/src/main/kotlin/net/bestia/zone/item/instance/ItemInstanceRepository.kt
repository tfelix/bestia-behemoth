package net.bestia.zone.item.instance

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
interface ItemInstanceRepository : JpaRepository<ItemInstance, Long>

fun ItemInstanceRepository.findByIdOrThrow(instanceId: Long): ItemInstance =
  findByIdOrNull(instanceId)
    ?: throw IllegalStateException("No item instance with id $instanceId")
