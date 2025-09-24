package net.bestia.zone.item

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LootItemRepository : JpaRepository<LootItem, Long> {
  fun findAllByBestiaId(bestiaId: Long): List<LootItem>
}