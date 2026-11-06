package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.enchant.ItemEnchantmentCatalog
import net.bestia.zone.item.enchant.ItemEnchantmentDefinition
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/** Loads `item_enchantments.yml` into [ItemEnchantmentCatalog], in memory only, like the status effects. */
@Component
@Order(104)
class ItemEnchantmentImporterBootRunner(
  private val catalog: ItemEnchantmentCatalog,
) : CommandLineRunner {

  data class ItemEnchantmentYmlDto(
    val itemEnchantments: List<ItemEnchantmentDefinition>
  )

  override fun run(vararg args: String?) {
    val definitions = load()
    catalog.load(definitions)

    LOG.info { "Item enchantment catalog loaded: ${definitions.size} definitions" }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    fun load(): List<ItemEnchantmentDefinition> {
      val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

      return ClassPathResource("item_enchantments.yml").inputStream.use {
        objectMapper.readValue(it, ItemEnchantmentYmlDto::class.java)
      }.itemEnchantments
    }
  }
}
