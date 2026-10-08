package net.bestia.zone.master

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.SpawnPointAvailability
import net.bestia.zone.world.SpawnPointAvailability.Offer
import net.bestia.zone.world.settlement.SettlementFates
import org.junit.jupiter.api.Test

class MasterHomesTest {

  private val masters = mockk<MasterRepository>(relaxed = true)
  private val availability = mockk<SpawnPointAvailability>(relaxed = true) {
    every { homeNameOf(any()) } returns null
    every { homeNameOf(FALLEN) } returns "Old Town"
  }
  private val jobs = mockk<AsyncJobExecutor> {
    every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val fell = slot<(Int) -> Unit>()

  init {
    MasterHomes(masters, availability, jobs, mockk<SettlementFates> { every { onFell(capture(fell)) } returns Unit })
  }

  @Test
  fun `the masters of a fallen town move to the first home still on offer`() {
    every { availability.offered() } returns listOf(
      Offer(7, "New Town", "village", Vec3L(1, 2, 3)),
      Offer(8, "Far Town", "village", Vec3L(9, 9, 9)),
    )

    fell.captured(FALLEN)

    verifyOrder {
      availability.refresh()
      masters.rehome("Old Town", "New Town", 1, 2, 3)
    }
  }

  @Test
  fun `with no home left on offer the masters keep theirs`() {
    every { availability.offered() } returns emptyList()

    fell.captured(FALLEN)

    verify(exactly = 0) { masters.rehome(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a settlement that was never a home moves nobody`() {
    fell.captured(FALLEN + 1)

    verify(exactly = 0) { masters.rehome(any(), any(), any(), any(), any()) }
  }

  private companion object {
    const val FALLEN = 4
  }
}
