package net.bestia.zone.account.authentication

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bus.contract.KickOrder
import net.bestia.zone.bus.BusProperties
import net.bestia.zone.config.ZoneConfig
import net.bestia.zone.message.ConnectionTerminator
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertTrue

/** A kick from the stream closes the connection it was meant for, and never one opened after it. */
class KickOrderListenerTest {

  private val kickedAccounts = KickedAccounts(
    ZoneConfig(bestiaBaseSlotCount = 1, bestiaMaxSlotCount = 1, jwtAuthSecretKey = "", shardId = 1)
  )
  private val connections = mockk<ConnectionTerminator> {
    every { disconnect(any(), any()) } returns true
  }
  private val listener = KickOrderListener(mockk(), BusProperties(nodeId = "test"), kickedAccounts, connections)

  @Test
  fun `a fresh kick closes the connection and refuses older tokens`() {
    val kickedAt = System.currentTimeMillis() + 1_000

    listener.handle(KickOrder(ACCOUNT, "BANNED", kickedAt))

    verify { connections.disconnect(ACCOUNT, "BANNED") }
    assertTrue(kickedAccounts.refuses(ACCOUNT, Date(kickedAt - 1_000)))
  }

  @Test
  fun `a kick from before the node started is remembered but closes nothing`() {
    val kickedAt = System.currentTimeMillis() - 60_000

    listener.handle(KickOrder(ACCOUNT, "GM_KICK", kickedAt))

    verify(exactly = 0) { connections.disconnect(any(), any()) }
    assertTrue(kickedAccounts.refuses(ACCOUNT, Date(kickedAt - 1_000)))
  }

  private companion object {
    const val ACCOUNT = 3L
  }
}
