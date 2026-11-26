package net.bestia.zone.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.doomsday.DoomEnding
import net.bestia.zone.doomsday.WorldEndedEvent
import net.bestia.zone.message.ConnectionTerminator
import net.bestia.zone.message.OutMessageHandler
import net.bestia.zone.socket.ZoneReadinessService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class WorldEndRestartTest {

  @Test
  fun `an ended world sends everyone away and stops the zone`() {
    val readiness = ZoneReadinessService().also { it.markReady() }
    val connections = mockk<OutMessageHandler> {
      every { connectedAccountIds } returns setOf(1L, 2L)
    }
    val terminator = mockk<ConnectionTerminator>(relaxed = true)
    val exitCode = CompletableFuture<Int>()

    WorldEndRestart(readiness, connections, terminator, mockk()) { exitCode.complete(it) }
      .onWorldEnded(WorldEndedEvent(DoomEnding.LOST))

    assertFalse(readiness.isReady())
    verify { terminator.disconnect(1L, "WORLD_ENDED") }
    verify { terminator.disconnect(2L, "WORLD_ENDED") }
    assertEquals(75, exitCode.get(10, TimeUnit.SECONDS))
  }
}
