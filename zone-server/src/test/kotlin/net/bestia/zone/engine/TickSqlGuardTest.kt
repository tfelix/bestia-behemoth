package net.bestia.zone.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class TickSqlGuardTest {

  private fun onThread(name: String, block: () -> Unit): Throwable? {
    var failure: Throwable? = null
    thread(name = name) {
      try {
        block()
      } catch (e: Throwable) {
        failure = e
      }
    }.join()

    return failure
  }

  @Test
  fun `SQL off the tick thread passes untouched`() {
    val guard = TickSqlGuard(failOnTick = true)

    assertEquals("select 1", guard.inspect("select 1"))
  }

  @Test
  fun `SQL on the tick thread is counted and may fail`() {
    val before = TickSqlGuard.violations.get()

    val failure = onThread(ZoneEngine.TICK_THREAD_NAME) { TickSqlGuard(failOnTick = true).inspect("select 1") }
    val logged = onThread(ZoneEngine.TICK_THREAD_NAME) { TickSqlGuard(failOnTick = false).inspect("select 1") }

    assertThrows<IllegalStateException> { throw failure!! }
    assertEquals(null, logged)
    assertEquals(before + 2, TickSqlGuard.violations.get())
  }
}
