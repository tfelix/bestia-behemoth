package net.bestia.zone.item.container

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
interface ItemContainerRepository : JpaRepository<ItemContainer, Long>

fun ItemContainerRepository.findByIdOrThrow(containerId: Long): ItemContainer =
  findByIdOrNull(containerId)
    ?: throw IllegalStateException("No item container with id $containerId")
