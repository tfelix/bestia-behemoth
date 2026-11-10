package net.bestia.zone.honor

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.economy.PersonalPrice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HonorPriceTest {

  private val fixture = HonorFixture()
  private val world = fixture.world
  private val merchant = world.createEntity { }

  @Test
  fun `a new master pays the town's price`() {
    val price = priceFor(HonorLedger())!!

    assertEquals(1.0, price.buyFactor)
    assertEquals(1.0, price.sellFactor)
  }

  @Test
  fun `below zero a merchant charges more and pays as much less`() {
    // 20 - 60 + 10 = -30: half again on top.
    val price = priceFor(HonorLedger(debt = 60, lastLossAt = fixture.now))!!

    assertEquals(1.5, price.buyFactor, 1e-9)
    assertEquals(0.5, price.sellFactor, 1e-9)
  }

  @Test
  fun `below -60 the merchant refuses`() {
    assertNull(priceFor(HonorLedger(debt = 91, lastLossAt = fixture.now)))
  }

  @Test
  fun `the best customer still pays more than the shop pays them`() {
    val price = priceFor(HonorLedger(earned = 1_000))!!

    assertEquals(0.95, price.buyFactor, 1e-9)
    assertEquals(1.0, price.sellFactor)
  }

  private fun priceFor(ledger: HonorLedger): PersonalPrice? {
    val master = fixture.master(Vec3L(0, 0, 0), ledger)

    return fixture.service.priceFor(world, master, HonorFixture.VILLAGE, merchant)
  }
}
