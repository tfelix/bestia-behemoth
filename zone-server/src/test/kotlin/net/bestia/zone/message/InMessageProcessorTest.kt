package net.bestia.zone.message

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass
import kotlin.test.assertEquals

class InMessageProcessorTest {

  private data class Poke(override val playerId: Long) : CMSG

  private class PokeHandler(override val lane: HandlerLane) : InMessageProcessor.IncomingMessageHandler<Poke> {
    override val handles: KClass<Poke> = Poke::class
    var handled = 0

    override fun handle(msg: Poke): Boolean {
      handled++
      return true
    }
  }

  @Test
  fun `handlers of one message must agree on their lane`() {
    assertThrows<IllegalArgumentException> {
      InMessageProcessor(listOf(PokeHandler(HandlerLane.TICK), PokeHandler(HandlerLane.IO))) { _, _, _ ->
        CompletableFuture.completedFuture(Unit)
      }
    }
  }

  @Test
  fun `a message is submitted on its handler's lane for its sender`() {
    val handler = PokeHandler(HandlerLane.IO)
    var submitted: Pair<Long, HandlerLane>? = null
    val sut = InMessageProcessor(listOf(handler)) { accountId, lane, task ->
      submitted = accountId to lane
      task()
      CompletableFuture.completedFuture(Unit)
    }

    sut.submit(Poke(playerId = 7L))

    assertEquals(7L to HandlerLane.IO, submitted)
    assertEquals(1, handler.handled)
  }
}
