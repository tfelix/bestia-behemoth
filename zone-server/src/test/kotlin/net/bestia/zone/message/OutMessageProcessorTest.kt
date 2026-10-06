package net.bestia.zone.message

import io.mockk.mockk
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.aoi.ActivePlayerAOIService
import net.bestia.zone.aoi.RecordingEntityVisibility
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.ActivePlayer
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.aoi.EntityAudience
import net.bestia.zone.world.stream.InterestRecipients
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class OutMessageProcessorTest {

  private val world = testWorld()
  private val visibility = RecordingEntityVisibility()

  private val sent = mutableListOf<Pair<Long, SMSG>>()
  private val handler = object : OutMessageHandler {
    override fun sendMessage(playerId: Long, outMessage: SMSG) {
      sent.add(playerId to outMessage)
    }
  }

  /** Off the tick the outbox takes nothing, so every send goes straight to the handler. */
  private val processor = OutMessageProcessor(
    recipients = InterestRecipients(EntityAudience(visibility), ActivePlayerAOIService(), mockk(relaxed = true)),
    outMessageHandler = handler,
    outbox = TickOutbox(handler),
  )

  private val event = object : SMSG {
    override fun toBnetEnvelope(): EnvelopeProto.Envelope = EnvelopeProto.Envelope.getDefaultInstance()
  }

  @Test
  fun `an event goes to the accounts that see the entity`() {
    val mob = world.createEntity { }
    visibility.observers[mob] = setOf(WATCHER, OTHER_WATCHER)

    processor.sendToObserversOf(world, mob, event)

    assertEquals(setOf(WATCHER, OTHER_WATCHER), sent.map { it.first }.toSet())
    sent.forEach { (_, message) -> assertSame(event, message) }
  }

  @Test
  fun `a player hears about its own entity even before it holds the chunk`() {
    val player = world.createEntity { id ->
      add(id, Account(OWNER))
      add(id, ActivePlayer)
    }
    visibility.observers[player] = setOf(WATCHER)

    processor.sendToObserversOf(world, player, event)

    assertEquals(setOf(WATCHER, OWNER), sent.map { it.first }.toSet())
  }

  private companion object {
    const val OWNER = 1L
    const val WATCHER = 2L
    const val OTHER_WATCHER = 3L
  }
}
