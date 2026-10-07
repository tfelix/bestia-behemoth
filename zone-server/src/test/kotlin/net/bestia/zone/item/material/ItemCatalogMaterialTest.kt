package net.bestia.zone.item.material

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

/** Reads the real `items.yml`, so a missing material fails here rather than only when a server boots. */
class ItemCatalogMaterialTest {

  @Test
  fun `every catalogue item names a known material`() {
    val known = ItemMaterial.entries.map { it.name }.toSet()
    val items = ClassPathResource("items.yml").inputStream.use { ObjectMapper(YAMLFactory()).readTree(it) }["items"]

    assertTrue(items.size() > 0, "items.yml lists no items")
    items.forEach { item ->
      val material = item["material"]?.asText()
      assertTrue(material in known, "${item["item-db-name"].asText()} names material $material")
    }
  }
}
