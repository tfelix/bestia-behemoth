package net.bestia.zone.message

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.socket.OutMessageHandler
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TickOutboxTest {

  private val handler = mockk<OutMessageHandler>(relaxed = true)
  private val sut = TickOutbox(handler)

  private fun vanish(entity: Long): SMSG {
    return VanishEntitySMSG(entity, VanishEntitySMSG.VanishKind.GONE)
  }

  @Test
  fun `sends offered during a collect leave as one batch per account, in order`() {
    sut.collect {
      sut.offer(1L, listOf(vanish(10)))
      sut.offer(2L, listOf(vanish(20)))
      sut.offer(1L, listOf(vanish(11)))
    }

    verify(exactly = 1) { handler.sendMessages(1L, listOf(vanish(10), vanish(11))) }
    verify(exactly = 1) { handler.sendMessages(2L, listOf(vanish(20))) }
  }

  @Test
  fun `nothing is taken outside a collect or from another thread`() {
    assertFalse(sut.offer(1L, listOf(vanish(10))))

    sut.collect {
      var taken = true
      thread { taken = sut.offer(1L, listOf(vanish(10))) }.join()
      assertFalse(taken)
      assertTrue(sut.offer(1L, listOf(vanish(11))))
    }
  }

  @Test
  fun `one account that fails does not stop the others`() {
    every { handler.sendMessages(1L, any()) } throws IllegalStateException("gone")

    sut.collect {
      sut.offer(1L, listOf(vanish(10)))
      sut.offer(2L, listOf(vanish(20)))
    }

    verify { handler.sendMessages(2L, listOf(vanish(20))) }
  }
}
