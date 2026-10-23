package net.bestia.api.push

import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushPreferenceTest {

  @Test
  fun `quiet hours that wrap past midnight cover the night in the player's own zone`() {
    val preference = PushPreference(1L, quietFrom = 22 * 60, quietTo = 7 * 60, timeZone = "Europe/Berlin")

    assertTrue(preference.isQuietAt(ZonedDateTime.parse("2026-10-21T21:30:00Z")))
    assertTrue(preference.isQuietAt(ZonedDateTime.parse("2026-10-22T04:59:00Z")))
    assertFalse(preference.isQuietAt(ZonedDateTime.parse("2026-10-22T05:00:00Z")))
    assertFalse(preference.isQuietAt(ZonedDateTime.parse("2026-10-21T19:59:00Z")))
  }

  @Test
  fun `without quiet hours nothing is quiet`() {
    assertFalse(PushPreference.defaults(1L).isQuietAt(ZonedDateTime.now()))
  }
}
