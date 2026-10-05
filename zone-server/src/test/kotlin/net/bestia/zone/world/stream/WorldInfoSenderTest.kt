package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.account.AccountConnectedEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * A body protected after a disconnect stays in the world and keeps being streamed to, so the server believes
 * chunks were offered that the reconnected client never received - and the client never asks for them.
 */
class WorldInfoSenderTest {

  private val subscriptions = ChunkSubscriptionService()
  private val inbox = ChunkStreamInbox()

  private val sender = WorldInfoSender(
    worldService = mockk(relaxed = true),
    chunkService = mockk(relaxed = true),
    inbox = inbox,
    outMessageProcessor = mockk(relaxed = true),
    bestiaClock = mockk(relaxed = true),
    settings = ChunkStreamConfig()
  )

  private val system = ChunkStreamSystem(
    chunkService = mockk(relaxed = true) { every { normalise(any()) } answers { firstArg() } },
    subscriptions = subscriptions,
    inbox = inbox,
    fanOut = mockk(relaxed = true),
    settings = ChunkStreamConfig(),
    groundHeight = mockk(relaxed = true),
    oreYield = mockk(relaxed = true),
    connections = mockk(relaxed = true),
  )

  @Test
  fun `a reconnecting account is streamed from scratch`() {
    subscriptions.applyManifest(ACCOUNT, listOf(ChunkPos(0, 0)), emptyList(), reset = true)

    sender.handleAccountConnected(AccountConnectedEvent(this, ACCOUNT, emptySet()))
    system.applyResets()

    assertTrue(subscriptions.announcedTo(ACCOUNT).isEmpty())
  }

  private companion object {
    const val ACCOUNT = 1L
  }
}
