package net.bestia.zone.socket

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.netty.buffer.Unpooled
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlowConsumerGuardTest {

  private val meters = SimpleMeterRegistry()

  private val channel = EmbeddedChannel().apply {
    freezeTime()
    config().writeBufferWaterMark = WriteBufferWaterMark(LOW_MARK, HIGH_MARK)
    pipeline().addLast(SlowConsumerGuard(TIMEOUT_SECONDS, MAX_BACKLOG, SocketTraffic(meters)))
  }

  private fun dropped(reason: String): Double {
    return meters.get("zone.socket.dropped").tag("reason", reason).counter().count()
  }

  @AfterEach
  fun releaseBuffers() {
    channel.finishAndReleaseAll()
  }

  @Test
  fun `a client that stays unwritable is dropped once the timeout passes`() {
    fillPastHighMark()

    advanceSeconds(TIMEOUT_SECONDS - 1)
    assertTrue(channel.isOpen, "a short stall must be tolerated")

    advanceSeconds(1)
    assertFalse(channel.isOpen)
    assertEquals(1.0, dropped("unwritable"))
  }

  @Test
  fun `a client that drains in time is kept`() {
    fillPastHighMark()

    channel.flush()
    channel.runPendingTasks()
    assertTrue(channel.isWritable)

    advanceSeconds(TIMEOUT_SECONDS * 2)
    assertTrue(channel.isOpen)
  }

  @Test
  fun `a backlog past the cap drops the client at once`() {
    channel.write(Unpooled.wrappedBuffer(ByteArray((MAX_BACKLOG * 2).toInt())))

    assertFalse(channel.isOpen)
    assertEquals(1.0, dropped("backlog"))
  }

  private fun fillPastHighMark() {
    channel.write(Unpooled.wrappedBuffer(ByteArray(HIGH_MARK * 2)))
    channel.runPendingTasks()
    assertFalse(channel.isWritable)
  }

  private fun advanceSeconds(seconds: Long) {
    channel.advanceTimeBy(seconds, TimeUnit.SECONDS)
    channel.runScheduledPendingTasks()
  }

  private companion object {
    const val LOW_MARK = 1024
    const val HIGH_MARK = 4096
    const val TIMEOUT_SECONDS = 10L
    const val MAX_BACKLOG = 64L * 1024
  }
}
