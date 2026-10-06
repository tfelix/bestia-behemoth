package net.bestia.zone.account

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.persistence.PlayerBestia
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import net.bestia.zone.account.persistence.Master

class PlayerBestiaPolicyTest {

  private val slots = mockk<AvailableBestiaSlotService> { every { getTotalSlotCount(ACCOUNT_ID) } returns SLOTS }
  private val sut = PlayerBestiaPolicy(slots)

  @Test
  fun `a master with a free slot may take another bestia`() {
    assertDoesNotThrow { sut.checkPolicy(masterOwning(SLOTS - 1), mockk()) }
  }

  @Test
  fun `a master with every slot taken may not`() {
    assertThrows<OwnedBestiaPolicyViolationException> { sut.checkPolicy(masterOwning(SLOTS), mockk()) }
  }

  private fun masterOwning(count: Int): Master {
    return mockk {
      every { account.id } returns ACCOUNT_ID
      every { bestias.ownedBestias } returns List(count) { mockk<PlayerBestia>() }
    }
  }

  private companion object {
    const val ACCOUNT_ID = 11L
    const val SLOTS = 4
  }
}
