package net.bestia.zone.economy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersonalPriceTest {

  @Test
  fun `haggling takes a share off the buy price and puts it on the sell price`() {
    val haggled = PersonalPrice.NEUTRAL.haggled(0.1)

    assertEquals(0.9, haggled.buyFactor, 1e-9)
    assertEquals(1.1, haggled.sellFactor, 1e-9)
  }

  @Test
  fun `the best honor and the best haggling still lose money on a round trip`() {
    val best = PersonalPrice(buyFactor = 0.95, sellFactor = 1.0).haggled(0.1)

    val paid = (1.0 + Shop.SPREAD) * best.buyFactor
    val gotBack = (1.0 - Shop.SPREAD) * best.sellFactor
    assertTrue(paid > gotBack, "paid $paid, got back $gotBack")
  }
}
