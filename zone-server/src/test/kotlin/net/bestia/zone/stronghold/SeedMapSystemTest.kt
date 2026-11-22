package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.cartography.poi.MapPoi
import net.bestia.zone.cartography.poi.MapPoiIndex
import net.bestia.zone.cartography.render.MapService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.stronghold.ecs.SeedMapSystem
import net.bestia.zone.stronghold.persistence.StrongholdRecord.State
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class SeedMapSystemTest {

  private val discovered = seedAt(1, "Hochvein")
  private val hidden = seedAt(2, "Goldgrund")
  private val held = seedAt(3, "Steinmark")
  private val onMap = slot<List<MapPoi>>()

  private val system = SeedMapSystem(
    seeds = mockk { every { awake() } returns listOf(discovered, hidden, held) },
    discoveries = mockk { every { isDiscoveredByAnyone(any()) } answers { firstArg<Int>() == 1 } },
    strongholds = mockk {
      every { on(any()) } answers {
        if (firstArg<Int>() == 3) Stronghold(3, 7L, State.STANDING, null, Instant.EPOCH, Instant.EPOCH) else null
      }
    },
    pois = mockk<MapPoiIndex> { every { setStrongholdSeeds(capture(onMap)) } returns Unit },
  )

  @Test
  fun `a discovered seed and every Stronghold are on the map, named after their seed, and no other seed`() {
    system.update(testWorld(), 10f)

    assertEquals(
      listOf(MapService.EMPYRITE_SEED to "Hochvein", MapService.STRONGHOLD to "Steinmark"),
      onMap.captured.map { it.kind to it.settlement },
    )
  }

  private fun seedAt(rank: Int, name: String) = EmpyriteSeed(rank, x = rank * 1_000.0, y = 0.0, name = name, settlement = -1)
}
