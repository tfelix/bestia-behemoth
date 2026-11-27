package net.bestia.zone.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bus.WorldRebirth
import net.bestia.bus.contract.LastWorld
import net.bestia.zone.doomsday.DoomEnding
import net.bestia.zone.doomsday.DoomKind
import net.bestia.zone.doomsday.WorldEndedEvent
import net.bestia.zone.doomsday.persistence.WorldLegacy
import net.bestia.zone.message.ConnectionTerminator
import net.bestia.zone.message.OutMessageHandler
import net.bestia.zone.socket.ZoneReadinessService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class WorldEndRestartTest {

  @Test
  fun `an ended world sends everyone away, tells the login server and stops the zone`() {
    val readiness = ZoneReadinessService().also { it.markReady() }
    val connections = mockk<OutMessageHandler> {
      every { connectedAccountIds } returns setOf(1L, 2L)
    }
    val terminator = mockk<ConnectionTerminator>(relaxed = true)
    val rebirth = mockk<WorldRebirth>(relaxed = true)
    val exitCode = CompletableFuture<Int>()

    WorldEndRestart(readiness, connections, terminator, rebirth, mockk()) { exitCode.complete(it) }
      .onWorldEnded(WorldEndedEvent(legacy))

    assertFalse(readiness.isReady())
    verify { rebirth.begin(LastWorld("Genesis", "VOID_TEAR", "LOST", 300, 100, 2, ENDED_AT.toEpochMilli())) }
    verify { terminator.disconnect(1L, "WORLD_ENDED") }
    verify { terminator.disconnect(2L, "WORLD_ENDED") }
    assertEquals(75, exitCode.get(10, TimeUnit.SECONDS))
  }

  private val legacy = WorldLegacy(
    worldName = "Genesis",
    seed = 7,
    threat = DoomKind.VOID_TEAR,
    ending = DoomEnding.LOST,
    daysLived = 300,
    tPercent = 100,
    townsLost = 2,
    bornAt = ENDED_AT.minusSeconds(300 * 86_400L),
    endedAt = ENDED_AT,
  )

  private companion object {
    val ENDED_AT: Instant = Instant.parse("2027-06-01T12:00:00Z")
  }
}
