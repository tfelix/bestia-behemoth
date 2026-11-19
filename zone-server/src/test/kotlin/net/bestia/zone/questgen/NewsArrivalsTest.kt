package net.bestia.zone.questgen

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.questgen.ending.NewsArrivals
import net.bestia.zone.townsfolk.persistence.Rumour
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourRegistry
import net.bestia.zone.townsfolk.rumour.RumourService
import org.junit.jupiter.api.Test

class NewsArrivalsTest {

  private val ecs = testWorld()

  // Runs what is posted at once, as the next tick would.
  private val view = mockk<WorldView> {
    every { post(any()) } answers { firstArg<World.() -> Unit>().invoke(ecs) }
  }
  private val told = Rumour(id = RUMOUR, settlement = FROM, kind = RumourKind.TOWN_FELL)
  private val registry = mockk<RumourRegistry> { every { heardBy(FROM) } returns listOf(told) }
  private val rumours = mockk<RumourService>(relaxed = true)

  private val arrivals = NewsArrivals(view, registry, rumours)

  @Test
  fun `the town that was told talks of the news from then on`() {
    arrivals.arrive(carryNewsPlan(FROM, TO, RUMOUR))

    verify { rumours.retell(told, TO) }
  }

  @Test
  fun `news its own town forgot on the way arrives nowhere`() {
    arrivals.arrive(carryNewsPlan(FROM, TO, rumourId = 99))

    verify(exactly = 0) { rumours.retell(any(), any()) }
  }

  @Test
  fun `a quest that is not about news tells no town anything`() {
    arrivals.arrive(lostRelicPlan(artifact = 3, WorldTarget.townsfolk(1)))

    verify(exactly = 0) { rumours.retell(any(), any()) }
  }

  private companion object {
    const val FROM = 4
    const val TO = 6
    const val RUMOUR = 12L
  }
}
