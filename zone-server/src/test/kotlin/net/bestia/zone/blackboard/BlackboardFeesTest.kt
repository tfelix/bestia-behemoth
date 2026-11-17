package net.bestia.zone.blackboard

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BlackboardFeesTest {

  @Test
  fun `the fee is the percent of the pay, rounded up, at least one coin`() {
    assertEquals(24, BlackboardFees.feeFor(pay = 480, percent = 5))
    assertEquals(4, BlackboardFees.feeFor(pay = 101, percent = 3))
    assertEquals(1, BlackboardFees.feeFor(pay = 2, percent = 3))
  }
}
