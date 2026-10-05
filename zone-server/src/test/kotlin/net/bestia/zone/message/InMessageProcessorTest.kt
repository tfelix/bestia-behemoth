package net.bestia.zone.message

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.AccountId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class InMessageProcessorTest {

  private data class Poke(override val playerId: Long) : CMSG

  private class TickPokeHandler : TickMessageHandler<Poke> {
    override val wire = decoder(MessageCase.PING) { accountId, _ -> Poke(accountId) }
    var handledWith: World? = null

    override fun handle(world: World, msg: Poke): Boolean {
      handledWith = world
      return true
    }
  }

  private class IoPokeHandler : IoMessageHandler<Poke> {
    override val wire = decoder(MessageCase.PING) { accountId, _ -> Poke(accountId) }
    var handled = 0

    override fun handle(msg: Poke): Boolean {
      handled++
      return true
    }
  }

  /** Runs every task at once and records which kind of work it was asked to do, and for whom. */
  private class RecordingInbox(private val world: World) : AccountTaskExecutor {
    var submitted: Pair<AccountId, String>? = null

    override fun onTick(accountId: AccountId, task: World.() -> Unit): CompletableFuture<Unit> {
      submitted = accountId to "tick"
      world.task()
      return CompletableFuture.completedFuture(Unit)
    }

    override fun onIo(accountId: AccountId, task: () -> Unit): CompletableFuture<Unit> {
      submitted = accountId to "io"
      task()
      return CompletableFuture.completedFuture(Unit)
    }
  }

  private val world = testWorld()

  @Test
  fun `a message type may have only one handler`() {
    assertThrows<IllegalArgumentException> {
      InMessageProcessor(listOf(TickPokeHandler(), IoPokeHandler()), RecordingInbox(world))
    }
  }

  @Test
  fun `a tick handler runs on the tick with the world, for the message's sender`() {
    val handler = TickPokeHandler()
    val inbox = RecordingInbox(world)

    InMessageProcessor(listOf(handler), inbox).submit(Poke(playerId = 7L))

    assertEquals(7L to "tick", inbox.submitted)
    assertEquals(world, handler.handledWith)
  }

  @Test
  fun `an IO handler runs on the IO lane, for the message's sender`() {
    val handler = IoPokeHandler()
    val inbox = RecordingInbox(world)

    InMessageProcessor(listOf(handler), inbox).submit(Poke(playerId = 7L))

    assertEquals(7L to "io", inbox.submitted)
    assertEquals(1, handler.handled)
  }
}
