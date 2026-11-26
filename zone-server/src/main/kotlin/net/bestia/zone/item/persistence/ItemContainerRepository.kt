package net.bestia.zone.item.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
interface ItemContainerRepository : JpaRepository<ItemContainer, Long> {

  /** A native delete, because the quiver is an element collection, which JPQL cannot bulk delete. */
  @Modifying
  @Query("DELETE FROM item_container_quiver", nativeQuery = true)
  fun clearAllQuivers(): Int
}

fun ItemContainerRepository.findByIdOrThrow(containerId: Long): ItemContainer =
  findByIdOrNull(containerId)
    ?: throw IllegalStateException("No item container with id $containerId")
