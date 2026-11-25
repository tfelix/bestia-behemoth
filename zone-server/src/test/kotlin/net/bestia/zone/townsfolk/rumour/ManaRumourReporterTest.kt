package net.bestia.zone.townsfolk.rumour

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.mana.ManaTiers.Change
import net.bestia.zone.world.time.BestiaClock
import org.junit.jupiter.api.Test

class ManaRumourReporterTest {

  private val rumours = mockk<RumourService>(relaxed = true)
  private var day = 10.0
  private val clock = mockk<BestiaClock> { every { now().absoluteDay } answers { day } }
  private val worldService = mockk<WorldService> { every { generated.config.voxelSize } returns 1.0 }

  private val sut = ManaRumourReporter(rumours, worldService, clock)
  private val world = testWorld()

  @Test
  fun `a rise posts the news of its tier once for a whole patch of land`() {
    sut.onTiersChanged(world, (0 until 20).map { Change(Cell(it, 0), ManaTier.CALM, ManaTier.UNEASY) })

    verify(exactly = 1) { rumours.post(RumourKind.STRANGE_LIGHTS, any(), any(), any()) }
  }

  @Test
  fun `the same news is not posted again while it still lives`() {
    val rise = listOf(Change(Cell(0, 0), ManaTier.UNEASY, ManaTier.OMINOUS))

    sut.onTiersChanged(world, rise)
    day += 5
    sut.onTiersChanged(world, rise)
    day += RumourKind.RESTLESS_BEASTS.lifetimeDays
    sut.onTiersChanged(world, rise)

    verify(exactly = 2) { rumours.post(RumourKind.RESTLESS_BEASTS, any(), any(), any()) }
  }

  @Test
  fun `a fall is no news`() {
    sut.onTiersChanged(world, listOf(Change(Cell(0, 0), ManaTier.STORMS, ManaTier.OMINOUS)))

    verify(exactly = 0) { rumours.post(any(), any(), any(), any()) }
  }
}
