package net.bestia.zone.chat

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.account.Authority
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.environment.time.WorldTimeSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Almost all of this is the command's grammar, which is where a chat command actually goes wrong: a form that
 * silently fails to match falls through to "not supported", and the caller reads that as the feature being
 * missing rather than as their having typed it differently.
 */
class WorldTimeChatCommandTest {

  private val current = BestiaDateTime(year = 2, month = 3, day = 11, hour = 15, minute = 0, second = 0)

  private val clock = mockk<BestiaClock>(relaxed = true) {
    every { now() } returns current
    every { speedFactor } returns BestiaDateTime.SPEED_FACTOR
    every { isShifted } returns false
    every { jumpTo(any()) } answers { firstArg() }
  }

  private val out = mockk<OutMessageProcessor>(relaxed = true)

  private val command = WorldTimeChatCommand(clock, out)

  private fun run(text: String) = command.tryExecute(PLAYER, text, setOf(Authority.WORLD_TIME))

  @Test
  fun `a bare date reports without moving the clock`() {
    assertTrue(run("/date"))

    verify(exactly = 0) { clock.jumpTo(any()) }
    verify(exactly = 0) { out.sendToAllConnected(any()) }
  }

  @Test
  fun `a time alone keeps the current date`() {
    assertTrue(run("/date 03:05"))

    val target = slot<BestiaDateTime>()
    verify { clock.jumpTo(capture(target)) }

    assertEquals(
      BestiaDateTime(year = 2, month = 3, day = 11, hour = 3, minute = 5, second = 0),
      target.captured
    )
    assertTrue(target.captured.isNight)
  }

  @Test
  fun `a full date sets every field`() {
    assertTrue(run("/date 7-4-29 22:15"))

    val target = slot<BestiaDateTime>()
    verify { clock.jumpTo(capture(target)) }

    assertEquals(
      BestiaDateTime(year = 7, month = 4, day = 29, hour = 22, minute = 15, second = 0),
      target.captured
    )
  }

  @Test
  fun `a single-digit hour is accepted`() {
    assertTrue(run("/date 3:05"))

    verify { clock.jumpTo(BestiaDateTime(year = 2, month = 3, day = 11, hour = 3, minute = 5, second = 0)) }
  }

  @Test
  fun `reset drops the shift instead of jumping`() {
    assertTrue(run("/date reset"))

    verify { clock.resetToRealTime() }
    verify(exactly = 0) { clock.jumpTo(any()) }
  }

  @Test
  fun `a jump is pushed to every connected client`() {
    run("/date 02:00")

    val sent = slot<SMSG>()
    verify { out.sendToAllConnected(capture(sent)) }

    // The reading the clock actually landed on, not the one that was asked for - the two differ if the clock
    // clamps, and a client told the unclamped one would drift.
    assertEquals(
      WorldTimeSMSG.of(
        BestiaDateTime(year = 2, month = 3, day = 11, hour = 2, minute = 0, second = 0),
        BestiaDateTime.SPEED_FACTOR
      ),
      sent.captured
    )
  }

  /**
   * Out of range is answered, not thrown. [BestiaDateTime] rejects these in its own `init`, and letting that
   * reach `ChatCommandHandler` would turn a typo into "error.command_failed".
   */
  @Test
  fun `an impossible month is refused without moving the clock`() {
    assertTrue(run("/date 1-9-1 12:00"))

    verify(exactly = 0) { clock.jumpTo(any()) }
    verify(exactly = 0) { out.sendToAllConnected(any()) }
  }

  @Test
  fun `does not answer for something that only looks like the command`() {
    assertFalse(run("/dates 12:00"))
    assertFalse(run("/date noon"))
    assertFalse(run("/date 12:00 extra"))
  }

  @Test
  fun `a player without the authority is not served`() {
    assertFalse(command.tryExecute(PLAYER, "/date 02:00", setOf(Authority.MAP_MOVE)))

    verify(exactly = 0) { clock.jumpTo(any()) }
  }

  private companion object {
    private const val PLAYER = 42L
  }
}
