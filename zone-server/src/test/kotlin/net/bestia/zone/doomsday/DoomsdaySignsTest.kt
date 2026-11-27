package net.bestia.zone.doomsday

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.casting.sense.BrewingEvents
import net.bestia.zone.doomsday.persistence.StoredDoomsday
import net.bestia.zone.weather.DoomSky
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.persistence.PersistedWorld
import net.bestia.zone.worldevent.RunningEvents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class DoomsdaySignsTest {

  private val world = PersistedWorld(
    name = "Genesis",
    seed = 7L,
    widthCells = 64,
    heightCells = 64,
    cellSizeMetres = 1_000.0,
    chunkSize = 16,
    chunkHeight = 32,
    voxelSizeMetres = 1.0,
    seaLevelMetres = 0.0,
    wrapX = true,
    wrapY = true,
    pipelineVersion = 1L,
    blockPaletteVersion = 1L,
    chunkFormatVersion = 1,
    shapeVersion = 1L,
    createdAt = Instant.EPOCH,
  )
  private val worldService = mockk<WorldService> {
    every { config } returns world.toWorldConfig()
  }
  private val brewing = RunningEvents()
  private val signs = DoomsdaySigns(worldService, brewing)

  private fun doomsday(stage: DoomStage, steppedToDay: Double): StoredDoomsday {
    return StoredDoomsday(
      kind = DoomKind.VOID_TEAR, originX = 1_000.0, originY = 1_000.0, onsetDay = 100.0, unfoldDays = 200.0,
      progress = 0.5, stage = stage, steppedToDay = steppedToDay,
    )
  }

  @Test
  fun `nothing shows before the onset`() {
    signs.remember(doomsday(DoomStage.OMENS, steppedToDay = 50.0))

    assertEquals(DoomSky.CLEAR, signs.at(1_000, 1_000))
    assertEquals(emptyList<BrewingEvents.Brewing>(), brewing.brewing())
    assertNull(signs.known())
  }

  @Test
  fun `the sky and the senses show it once it began`() {
    signs.remember(doomsday(DoomStage.ESCALATION, steppedToDay = 150.0))

    assertEquals(DoomSky("VOID_TEAR", 3), signs.at(1_000, 1_000))
    assertEquals(DoomKind.VOID_TEAR to DoomStage.ESCALATION, signs.known())
    assertEquals(DoomSky.CLEAR, signs.at(33_000, 33_000), "far beyond its reach")
    val felt = brewing.brewing().single()
    assertEquals(BrewingEvents.Stage.GATHERING, felt.stage)
    assertEquals(10.0, felt.feltFrom)
  }

  @Test
  fun `its reach crosses the world's seam`() {
    signs.remember(doomsday(DoomStage.CATACLYSM, steppedToDay = 150.0))

    assertEquals(DoomSky("VOID_TEAR", 4), signs.at(64_000 - 500, 1_000))
  }
}
