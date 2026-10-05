package net.bestia.zone.item.loot

/** One row of a species' drop table as plain values, safe to keep after the session that loaded it is gone. */
data class LootDrop(val bestiaId: Long, val itemId: Long, val dropChance: Int)
