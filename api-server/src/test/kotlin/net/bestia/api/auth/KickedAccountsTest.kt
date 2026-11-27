package net.bestia.api.auth

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KickedAccountsTest {

  private val kicked = KickedAccounts(CompanionProperties(jwtSecret = "unused", kickMemoryMinutes = 20))
  private val now = Instant.now()

  @Test
  fun `a token from before the kick or from its second is refused, a later one is not`() {
    kicked.remember(1, now)

    assertTrue(kicked.refuses(1, now.minusSeconds(60)))
    assertTrue(kicked.refuses(1, now))
    assertFalse(kicked.refuses(1, now.plusSeconds(1)))
    assertFalse(kicked.refuses(2, now.minusSeconds(60)))
  }

  @Test
  fun `the later of two kicks read out of order counts`() {
    kicked.remember(1, now)
    kicked.remember(1, now.minusSeconds(30))

    assertTrue(kicked.refuses(1, now.minusSeconds(10)))
  }

  @Test
  fun `a kick older than the memory is not kept`() {
    kicked.remember(1, now.minusSeconds(21 * 60))

    assertFalse(kicked.refuses(1, now.minusSeconds(30 * 60)))
  }
}
