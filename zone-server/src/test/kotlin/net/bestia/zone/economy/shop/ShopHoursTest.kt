package net.bestia.zone.economy.shop

import net.bestia.zone.ai.core.state.HourWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShopHoursTest {

  @Test
  fun `a counter is open through the shift and shut outside it`() {
    val hours = ShopHours(HourWindow(7, 19), dayOffsetMinutes = 0)

    assertTrue(hours.isOpenAt(minute(7)))
    assertTrue(hours.isOpenAt(minute(18) + 59))
    assertFalse(hours.isOpenAt(minute(19)))
    assertFalse(hours.isOpenAt(minute(3)))
  }

  @Test
  fun `a merchant who keeps late hours opens and shuts late`() {
    val hours = ShopHours(HourWindow(7, 19), dayOffsetMinutes = 20)

    assertFalse(hours.isOpenAt(minute(7) + 10))
    assertTrue(hours.isOpenAt(minute(19) + 10))
    assertEquals(minute(7) + 20, hours.opensAtMinute)
    assertEquals(minute(19) + 20, hours.closesAtMinute)
  }

  @Test
  fun `hours an early riser moves before midnight wrap to the previous evening`() {
    val hours = ShopHours(HourWindow(0, 6), dayOffsetMinutes = -30)

    assertEquals(minute(23) + 30, hours.opensAtMinute)
  }

  @Test
  fun `a merchant who keeps no hours is always open`() {
    assertTrue(ShopHours.ALWAYS_OPEN.isOpenAt(minute(3)))
    assertNull(ShopHours.ALWAYS_OPEN.opensAtMinute)
  }

  private fun minute(hour: Int): Int {
    return hour * HourWindow.MINUTES_PER_HOUR
  }
}
