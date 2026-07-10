package net.bestia.zone.boot

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
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

  override fun tryUpdate(dto: ItemYamlDto, entity: Item): Boolean {
    val needsUpdate = entity.weight != dto.weight
      || entity.type != getType(dto)
      || entity.script != dto.script
      || entity.description != dto.description

    return if (needsUpdate) {
      // TODO this is a bit tricky because we need to preserve IDs but still might want to update. Maybe once this
      //   is in place we want to use a proper schema migration mechanism instead?
      true
    } else {
      false
    }
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

  companion object {
    private const val ITEMS_RESOURCE = "items.yml"

    private val LOG = KotlinLogging.logger { }
  }
}