package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.persistence.AsyncJobExecutor

/** Real coin handling over the live bag, with the database half left out. */
fun coinsPaidWith(coinItemId: Long): StrongholdCoins {
  return StrongholdCoins(
    inventoryService = mockk(relaxed = true),
    commodities = mockk { every { coinItemId() } returns coinItemId },
    reserve = mockk(relaxed = true),
    world = mockk(relaxed = true),
    asyncJobExecutor = mockk<AsyncJobExecutor>(relaxed = true),
  )
}
