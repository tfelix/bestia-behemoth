package net.bestia.zone.boot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.bestia.zone.crafting.discovery.CraftVector
import net.bestia.zone.item.stat.ItemStat
import net.bestia.zone.item.stat.StatPool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.ClassPathResource

/** Reads the real `items.yml` the way the importer does, so a broken template fails here and not at boot. */
class ItemStatImportTest {

  private val items = ClassPathResource("items.yml").inputStream.use {
    ObjectMapper(YAMLFactory()).registerKotlinModule()
      .readValue(it, ItemImporterBootRunner.ItemsYmlFile::class.java)
      .items
  }

  @Test
  fun `every item's stats and template build`() {
    items.forEach { it.toStatSheet() }
  }

  @Test
  fun `every material's craft axes lie in the feature space`() {
    items.filter { it.craftAxes.isNotEmpty() }.forEach { CraftVector(it.craftAxes) }
  }

  @Test
  fun `the iron sword keeps its fixed stats and rolls an attribute`() {
    val sheet = items.single { it.identifier == "iron_sword" }.toStatSheet()
    val template = requireNotNull(sheet.randomStats)

    assertEquals(mapOf(ItemStat.STR to 4, ItemStat.DEX to 1), sheet.stats)
    assertEquals(StatPool.ATTRIBUTE.stats, template.slots.first().candidates)
    assertEquals(listOf(10), template.socketChances)
  }

  @Test
  fun `an affix names a pool or a stat, not both`() {
    val both = ItemImporterBootRunner.AffixDto(pool = StatPool.ATTRIBUTE, stat = ItemStat.STR)

    assertThrows<IllegalArgumentException> { both.toSlot() }
  }

  @Test
  fun `only gear rolls`() {
    val apple = items.single { it.identifier == "apple" }
      .copy(randomStats = ItemImporterBootRunner.RandomStatsDto(sockets = listOf(50)))

    assertThrows<IllegalArgumentException> { apple.toStatSheet() }
  }
}
