package net.bestia.zone.boot

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Imports the items from the YML resources into the database.
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
    val weight: Int,
    val type: String
  )

  override fun tryUpdate(dto: ItemYamlDto, entity: Item): Boolean {
    val needsUpdate = entity.weight != dto.weight || entity.type != getType(dto)

    return if(needsUpdate) {
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

  override fun newEntity(dto: ItemYamlDto): Item {
    return Item(dto.identifier, dto.weight, getType(dto))
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
    private val LOG = KotlinLogging.logger { }
  }
}