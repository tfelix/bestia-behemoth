package net.bestia.zone.boot

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaRepository
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.LootItem
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Imports the mobs from the YML resources into the database.
 */
@Component
@Order(101)
class MobImporterBootRunner(
  private val itemRepository: ItemRepository,
  bestiaRepository: BestiaRepository,
) : CommandLineRunner,
  YmlImporterBootRunner<MobImporterBootRunner.MobYmlDto, Bestia>(
    "Mob",
    "mob",
    bestiaRepository,
    MobYmlDto::class.java
  ) {
  data class MobYmlDto(
    val identifier: String,
    val level: Int,
    val health: Int,
    val mana: Int,
    val experience: Int,
    val loot: List<Loot>
  ) {
    data class Loot(
      @JsonProperty("item")
      val itemIdentifier: String,
      val chance: Int
    )
  }

  override fun newEntity(dto: MobYmlDto): Bestia {
    val bestia = Bestia(
      identifier = dto.identifier,
      level = dto.level,
      mana = dto.mana,
      health = dto.health,
      experienceReward = dto.experience
    )

    createLootItem(bestia, dto)

    return bestia
  }

  private fun createLootItem(bestia: Bestia, dto: MobYmlDto) {
    dto.loot.forEach { lootItemDto ->
      val item = itemRepository.findByIdentifier(lootItemDto.itemIdentifier)

      if (item != null) {
        bestia.lootTable.add(
          LootItem(
            bestia = bestia,
            item = item,
            dropChance = lootItemDto.chance
          )
        )
      } else {
        LOG.warn { "Loot table item ${lootItemDto.itemIdentifier} was not found in the database, skipping it" }
      }
      lootItemDto.itemIdentifier
    }
  }

  override fun getEntityIdentifier(entity: Bestia): String {
    return entity.identifier
  }

  override fun getYmlIdentifier(dto: MobYmlDto): String {
    return dto.identifier
  }

  override fun tryUpdate(dto: MobYmlDto, entity: Bestia): Boolean {
    // TODO
    return false
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}