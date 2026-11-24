package net.bestia.zone.account

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.persistence.PlayerBestia
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import net.bestia.zone.account.persistence.Master
import kotlin.test.assertEquals

class PlayerBestiaPolicyTest {

  private val slots = mockk<AvailableBestiaSlotService> {
    every { getTotalSlotCount(ACCOUNT_ID) } returns ACTIVE_SLOTS
    every { getStorageSlotCount(ACCOUNT_ID) } returns STORAGE_SLOTS
  }
  private val sut = PlayerBestiaPolicy(slots)

  @Test
  fun `a new bestia takes a free active slot first`() {
    assertEquals(BestiaPlace.ACTIVE, sut.placeFor(masterOwning(active = ACTIVE_SLOTS - 1, stored = 0)))
  }

  @Test
  fun `with every active slot taken it goes into storage`() {
    assertEquals(BestiaPlace.STORAGE, sut.placeFor(masterOwning(active = ACTIVE_SLOTS, stored = STORAGE_SLOTS - 1)))
  }

  @Test
  fun `with storage full too there is no place`() {
    assertThrows<OwnedBestiaPolicyViolationException> {
      sut.placeFor(masterOwning(active = ACTIVE_SLOTS, stored = STORAGE_SLOTS))
    }
  }

  private fun masterOwning(active: Int, stored: Int): Master {
    return mockk {
      every { id } returns 1L
      every { account.id } returns ACCOUNT_ID
      every { bestias.activeBestias } returns List(active) { mockk<PlayerBestia>() }
      every { bestias.storedBestias } returns List(stored) { mockk<PlayerBestia>() }
    }
  }

  private companion object {
    const val ACCOUNT_ID = 11L
    const val ACTIVE_SLOTS = 4
    const val STORAGE_SLOTS = 20
  }
}
