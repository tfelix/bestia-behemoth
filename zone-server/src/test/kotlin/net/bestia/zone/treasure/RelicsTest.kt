package net.bestia.zone.treasure

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemStatRoller
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelicsTest {

  private val identifiers = mutableListOf<String>()

  private val relics = Relics(
    worldService = mockk<WorldService> { every { generated } returns world },
    records = mockk(relaxed = true),
    items = mockk { every { idOf(capture(identifiers)) } returns RELIC },
    stats = ItemStatCatalog(),
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
  fun `an artifact the chronicle does not know gives nothing`() {
    assertNull(relics.unearth(Int.MAX_VALUE))
  }

  private companion object {
    const val RELIC = 130L

    val world: GeneratedWorld
      get() {
        return TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
      }
  }
}
