package net.bestia.zone.treasure

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.zone.boot.ItemImporterBootRunner
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemStatRoller
import net.bestia.zone.item.stat.ItemStatSheet
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class RelicsTest {

  private val identifiers = mutableListOf<String>()

  private val relics = Relics(
    worldService = mockk<WorldService> { every { generated } returns world },
    records = mockk(relaxed = true),
    items = mockk { every { idOf(capture(identifiers)) } returns RELIC },
    stats = ItemStatCatalog().apply { load(mapOf(RELIC to relicBladeSheet())) },
    roller = ItemStatRoller(),
    asyncJobExecutor = mockk(relaxed = true),
  )

  @Test
  fun `a buried artifact comes up as its relic, named by the chronicle`() {
    val artifacts = world.world.chronicle.artifacts
    assertTrue(artifacts.isNotEmpty(), "the fixture world's history made no artifact")
    val artifact = artifacts.first()

    val relic = assertNotNull(relics.unearth(artifact.index))

    assertEquals(RELIC, relic.itemId)
    assertEquals("relic_${artifact.kind.name.lowercase()}", identifiers.last())
    assertTrue(relic.freshId != 0L, "the copy is named up front so its name can be kept")
    val name = assertNotNull(relics.of(relic.freshId))
    assertTrue(name.first().isUpperCase(), "the name is shown as a title: $name")
  }

  @Test
  fun `a relic comes up between item level 80 and 110`() {
    for (artifact in world.world.chronicle.artifacts) {
      val level = assertNotNull(relics.unearth(artifact.index)?.roll).itemLevel

      assertTrue(level in 80..110, "artifact ${artifact.index} came up at item level $level")
    }
  }

  @Test
  fun `an artifact the chronicle does not know gives nothing`() {
    assertNull(relics.unearth(Int.MAX_VALUE))
  }

  private companion object {
    const val RELIC = 130L

    /** The real sheet, so the test sees the base level and random stats items.yml gives a relic. */
    fun relicBladeSheet(): ItemStatSheet {
      val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      val items = ClassPathResource("items.yml").inputStream.use {
        mapper.readValue(it, ItemImporterBootRunner.ItemsYmlFile::class.java).items
      }
      return items.single { it.identifier == "relic_blade" }.toStatSheet()
    }

    val world: GeneratedWorld
      get() {
        return TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
      }
  }
}
