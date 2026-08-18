package net.bestia.zone.boot

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.equip.EquipmentSlot
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/**
 * Imports the items from the single `items.yml` resource into the database.
 */
@Component
@Order(100)
class ItemImporterBootRunner(
  itemRepository: ItemRepository
) : CommandLineRunner,
  YmlImporterBootRunner<ItemImporterBootRunner.ItemYamlDto, Item>(
    "Item",
    "item",
    itemRepository,
    ItemYamlDto::class.java
  ) {

  data class ItemYamlDto(
    @JsonProperty("item-db-name")
    val identifier: String,
    val id: Long,
    val weight: Int,
    val type: String,
    val script: String? = null,
    @JsonProperty("equip-slot")
    val equipSlot: String? = null,
    @JsonProperty("max-durability")
    val maxDurability: Int = 0,
    val description: String? = null
  )

  /**
   * Wrapper for the single `items.yml` file which holds all items under a top-level `items` list.
   */
  data class ItemsYmlFile(
    val items: List<ItemYamlDto> = emptyList()
  )

  override fun loadYmlItems(): List<ItemYamlDto> {
    val objectMapper = createYmlMapper()

    ClassPathResource(ITEMS_RESOURCE).inputStream.use { stream ->
      return objectMapper.readValue(stream, ItemsYmlFile::class.java).items
    }
  }

  /**
   * Copies an edited YML item over the row that already carries its id.
   *
   * The base class hands back the *managed* entity and re-saves that same object, so this has to **assign**
   * - returning `true` without writing anything, which is what this used to do, made the import log say
   * "1 updated" while the database kept the old values. `import()` is not transactional, so `entity` is
   * detached and the save is a merge; there is no dirty checking to fall back on.
   *
   * The identity columns are deliberately not touched. `id` is what inventories, loot tables and item
   * instances reference, and `identifier` is the key this row was matched on in the first place.
   */
  override fun tryUpdate(dto: ItemYamlDto, entity: Item): Boolean {
    val type = getType(dto)
    val equipSlot = getEquipSlot(dto)

    val needsUpdate = entity.weight != dto.weight
      || entity.type != type
      || entity.script != dto.script
      || entity.equipSlot != equipSlot
      || entity.maxDurability != dto.maxDurability
      || entity.description != dto.description

    if (!needsUpdate) {
      return false
    }

    entity.weight = dto.weight
    entity.type = type
    entity.script = dto.script
    entity.equipSlot = equipSlot
    entity.maxDurability = dto.maxDurability
    entity.description = dto.description
    // Derived from the type at construction, so it has to follow the type here too - an item changed from
    // EQUIP to ETC that kept `stackable = false` would silently stop merging in the inventory.
    entity.stackable = type != Item.ItemType.EQUIP
    entity.validate()

    return true
  }

  override fun getYmlIdentifier(dto: ItemYamlDto): String {
    return dto.identifier
  }

  override fun getEntityIdentifier(entity: Item): String {
    return entity.identifier
  }

  override fun getYmlId(dto: ItemYamlDto): Long {
    return dto.id
  }

  override fun newEntity(dto: ItemYamlDto): Item {
    return Item(
      id = dto.id,
      identifier = dto.identifier,
      weight = dto.weight,
      type = getType(dto),
      script = dto.script,
      equipSlot = getEquipSlot(dto),
      maxDurability = dto.maxDurability,
      description = dto.description
    )
  }

  private fun getType(dto: ItemYamlDto): Item.ItemType {
    return try {
      Item.ItemType.valueOf(dto.type.uppercase())
    } catch (ex: IllegalArgumentException) {
      LOG.warn { "Unknown item type '${dto.type}' for item '${dto.identifier}'" }
      throw ex
    }
  }

  private fun getEquipSlot(dto: ItemYamlDto): EquipmentSlot? {
    val slotName = dto.equipSlot ?: return null

    return try {
      EquipmentSlot.valueOf(slotName.uppercase())
    } catch (ex: IllegalArgumentException) {
      LOG.warn { "Unknown equip slot '$slotName' for item '${dto.identifier}'" }
      throw ex
    }
  }

  companion object {
    private const val ITEMS_RESOURCE = "items.yml"

    private val LOG = KotlinLogging.logger { }
  }
}