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
    val spawnWeight: Int = 100,

    /** True to keep this species out of the wild spawner entirely; scripts only. */
    @JsonProperty("event-only")
    val eventOnly: Boolean = false,

    /** Mean annual temperature window in degrees Celsius, or neither for no preference. */
    @JsonProperty("temperature-min")
    val temperatureMin: Double? = null,
    @JsonProperty("temperature-max")
    val temperatureMax: Double? = null
  ) {
    data class Loot(
      @JsonProperty("item")
      val itemIdentifier: String,
      val chance: Int
    )
  }

  override fun newEntity(dto: MobYmlDto): Bestia {
    validateTemperature(dto)

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
      spawnWeight = dto.spawnWeight,
      eventOnly = dto.eventOnly,
      temperatureMinCelsius = dto.temperatureMin,
      temperatureMaxCelsius = dto.temperatureMax
    )

    createLootItem(bestia, dto)

    return bestia
  }

  /**
   * Copies an edited YML mob over the row that already carries its id.
   *
   * This used to `return false` behind a TODO, and that was invisible for as long as no mob was ever edited.
   * It stopped being invisible the moment the catalogue grew fields the spawner reads: the dev datasource is
   * a persisted MariaDB with `ddl-auto: update`, so Hibernate would add `event_only` and the temperature
   * columns to the existing rows, back-fill them with the type's implicit default, and the importer would
   * leave them there while logging the mob as "not changed". A designer would set `temperature-min` and see
   * absolutely nothing happen, with no message anywhere saying why.
   *
   * The assignments are the whole point. `YmlImporterBootRunner.import` is not transactional, so `entity` is
   * detached and `repository.save` is a merge - there is no dirty checking to fall back on, and returning
   * `true` without writing anything merges a row identical to the one already there. That is the bug
   * `ItemImporterBootRunner` carried.
   *
   * `id` and `identifier` are deliberately untouched: the first is what loot tables, spawners and persisted
   * mobs reference, and the second is the key this row was matched on.
   *
   * The loot table and skills are out of scope. They are `@OneToMany(orphanRemoval = true)` collections, and
   * re-syncing a collection is a different problem from overwriting a scalar - a half-done version that
   * added rows without removing them would be worse than not doing it.
   */
  override fun tryUpdate(dto: MobYmlDto, entity: Bestia): Boolean {
    validateTemperature(dto)

    val habitat = parseHabitat(dto)
    val equipSlotMask = parseEquipSlotMask(dto)

    val changed = entity.level != dto.level ||
        entity.health != dto.health ||
        entity.mana != dto.mana ||
        entity.experienceReward != dto.experience ||
        entity.aiProfile != dto.ai ||
        entity.equipSlotMask != equipSlotMask ||
        entity.habitat != habitat ||
        entity.corruptedOnly != dto.corruptedOnly ||
        entity.boss != dto.boss ||
        entity.spawnWeight != dto.spawnWeight ||
        entity.eventOnly != dto.eventOnly ||
        entity.temperatureMinCelsius != dto.temperatureMin ||
        entity.temperatureMaxCelsius != dto.temperatureMax

    if (!changed) {
      return false
    }

    entity.level = dto.level
    entity.health = dto.health
    entity.mana = dto.mana
    entity.experienceReward = dto.experience
    entity.aiProfile = dto.ai
    entity.equipSlotMask = equipSlotMask
    entity.habitat = habitat
    entity.corruptedOnly = dto.corruptedOnly
    entity.boss = dto.boss
    entity.spawnWeight = dto.spawnWeight
    entity.eventOnly = dto.eventOnly
    entity.temperatureMinCelsius = dto.temperatureMin
    entity.temperatureMaxCelsius = dto.temperatureMax

    return true
  }

  /**
   * A temperature window is both bounds or neither, and the low one is low.
   *
   * Thrown rather than warned, for [parseHabitat]'s reason: a one-sided window is a designer's typo, and the
   * quiet outcome of accepting one is a species that draws at a penalty everywhere for the rest of the
   * world's life. Half a window carries no meaning worth guessing at.
   */
  private fun validateTemperature(dto: MobYmlDto) {
    val min = dto.temperatureMin
    val max = dto.temperatureMax

    require((min == null) == (max == null)) {
      "Mob '${dto.identifier}' sets only one of temperature-min/temperature-max; set both or neither"
    }

    if (min != null && max != null) {
      require(min <= max) {
        "Mob '${dto.identifier}' has temperature-min $min above temperature-max $max"
      }
    }
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

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}