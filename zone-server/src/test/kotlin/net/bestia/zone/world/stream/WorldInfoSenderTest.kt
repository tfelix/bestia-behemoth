package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.session.AccountConnectedEvent
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
    settings = ChunkStreamConfig(),
    connectMessageSources = emptyList(),
  )

  private val system = ChunkStreamSystem(
    chunkService = mockk(relaxed = true) { every { normalise(any()) } answers { firstArg() } },
    subscriptions = subscriptions,
    inbox = inbox,
    fanOut = mockk(relaxed = true),
    settings = ChunkStreamConfig(),
    workers = ChunkWorkers(java.util.concurrent.Executor { it.run() }),
    groundHeight = mockk(relaxed = true),
    carveYield = mockk(relaxed = true),
    connections = mockk(relaxed = true),
  )

  @Test
  fun `a reconnecting account is streamed from scratch`() {
    subscriptions.applyManifest(ACCOUNT, listOf(ChunkPos(0, 0)), emptyList(), reset = true)

    sender.handleAccountConnected(AccountConnectedEvent(this, ACCOUNT, emptySet()))
    system.applyResets()

    assertTrue(subscriptions.announcedTo(ACCOUNT).isEmpty())
  }

  /** A client that has not got them yet must not be sent a chunk, so they ride directly behind the world info. */
  @Test
  fun `what the slices above want a client to have follows the world info`() {
    val outbound = mockk<OutMessageProcessor>(relaxed = true)
    val extra = mockk<SMSG>()

    val readySender = WorldInfoSender(
      worldService = mockk(relaxed = true),
      chunkService = mockk(relaxed = true) { every { isReady } returns true },
      inbox = inbox,
      outMessageProcessor = outbound,
      bestiaClock = mockk(relaxed = true),
      settings = ChunkStreamConfig(),
      connectMessageSources = listOf(mockk { every { connectMessages() } returns listOf(extra) }),
    )

    readySender.handleAccountConnected(AccountConnectedEvent(this, ACCOUNT, emptySet()))

    verifyOrder {
      outbound.sendToPlayer(ACCOUNT, ofType<WorldInfoSMSG>())
      outbound.sendToPlayer(ACCOUNT, listOf(extra))
    }
  }

  @Test
  fun `nothing is sent before the world has been generated`() {
    val source = mockk<ConnectMessageSource>(relaxed = true)

    val notReady = WorldInfoSender(
      worldService = mockk(relaxed = true),
      chunkService = mockk(relaxed = true) { every { isReady } returns false },
      inbox = inbox,
      outMessageProcessor = mockk(relaxed = true),
      bestiaClock = mockk(relaxed = true),
      settings = ChunkStreamConfig(),
      connectMessageSources = listOf(source),
    )

    notReady.handleAccountConnected(AccountConnectedEvent(this, ACCOUNT, emptySet()))

    verify(exactly = 0) { source.connectMessages() }
  }

  private companion object {
    const val ACCOUNT = 1L
  }
}
