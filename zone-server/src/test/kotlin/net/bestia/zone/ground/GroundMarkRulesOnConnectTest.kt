package net.bestia.zone.ground

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.world.time.BestiaClock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroundMarkRulesOnConnectTest {

  private val clock = mockk<BestiaClock> { every { speedFactor } returns 4.0 }

  /** The message is built from the configs and the clock as they are when a client connects. */
  @Test
  fun `a connection is given the rules built from the live configs and clock`() {
    val stamps = GroundStampConfig(footprintTtlSeconds = 600, maxStampsPerColumn = 12)
    val sut = GroundMarkRulesOnConnect(stamps, GroundWearConfig(), GroundBloodConfig(), clock)

    val message = assertIs<GroundMarkRulesSMSG>(sut.connectMessages().single())

    assertEquals(150_000, message.stampLifetimeMillis)
    assertEquals(12, message.stampRingCapacity)
  }
}
