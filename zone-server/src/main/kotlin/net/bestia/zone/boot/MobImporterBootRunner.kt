package net.bestia.zone.boot

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.bestia.Bestia
import net.bestia.zone.bestia.BestiaRepository
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.loot.LootItem
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
    val id: Long,
    val identifier: String,
    val level: Int,
    val health: Int,
    val mana: Int,
    val experience: Int,
    val loot: List<Loot>,
    val ai: String? = null,
    @JsonProperty("equip-slots")
    val equipSlots: List<String> = emptyList(),

    /** Biome names a wild spawner may place this in; empty means the den's own rules decide alone. */
    val habitat: List<String> = emptyList(),
    @JsonProperty("corrupted-only")
    val corruptedOnly: Boolean = false,
    val boss: Boolean = false,
    @JsonProperty("spawn-weight")
    val spawnWeight: Int = 100
  ) {
    data class Loot(
      @JsonProperty("item")
      val itemIdentifier: String,
      val chance: Int
    )
  }

  override fun newEntity(dto: MobYmlDto): Bestia {
    val bestia = Bestia(
      id = dto.id,
      identifier = dto.identifier,
      level = dto.level,
      mana = dto.mana,
      health = dto.health,
      experienceReward = dto.experience,
      aiProfile = dto.ai,
      equipSlotMask = parseEquipSlotMask(dto),
      habitat = parseHabitat(dto),
      corruptedOnly = dto.corruptedOnly,
      boss = dto.boss,
      spawnWeight = dto.spawnWeight
    )

    createLootItem(bestia, dto)

    return bestia
  }

  /**
   * Validates the habitat names against the generator's own enum and joins them.
   *
   * Validated at import rather than at spawn time, because a typo here would otherwise make a species
   * silently unspawnable - which is the shipped-dead failure one level down, and invisible until somebody
   * notices they have never seen a blob.
   */
  private fun parseHabitat(dto: MobYmlDto): String {
    val names = dto.habitat.map { it.uppercase() }
    for (name in names) {
      if (net.bestia.worldgen.bio.Biome.entries.none { it.name == name }) {
        LOG.error { "Unknown habitat biome '$name' for mob '${dto.identifier}'" }
        throw IllegalArgumentException("Unknown habitat biome '$name' for mob '${dto.identifier}'")
      }
    }
    return names.joinToString(",")
  }

  private fun parseEquipSlotMask(dto: MobYmlDto): Int {
    val slots = dto.equipSlots.map { name ->
      try {
        EquipmentSlot.valueOf(name.uppercase())
      } catch (ex: IllegalArgumentException) {
        LOG.error { "Unknown equip slot '$name' for mob '${dto.identifier}'" }
        throw ex
      }
    }

    return EquipmentSlots.maskOf(slots)
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

  override fun getYmlId(dto: MobYmlDto): Long {
    return dto.id
  }

  override fun tryUpdate(dto: MobYmlDto, entity: Bestia): Boolean {
    // TODO
    return false
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}