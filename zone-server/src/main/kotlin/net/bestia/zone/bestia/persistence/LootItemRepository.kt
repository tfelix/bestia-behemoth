package net.bestia.zone.bestia.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import net.bestia.zone.bestia.loot.LootDrop

@Repository
interface LootItemRepository : JpaRepository<LootItem, Long> {
  fun findAllByBestiaId(bestiaId: Long): List<LootItem>

  @Query("select new net.bestia.zone.bestia.loot.LootDrop(l.bestia.id, l.item.id, l.dropChance) from LootItem l")
  fun findAllDrops(): List<LootDrop>
}