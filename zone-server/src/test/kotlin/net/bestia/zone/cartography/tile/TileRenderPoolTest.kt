package net.bestia.zone.cartography.tile

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every tile request waits on a servlet thread until its render is done. Without bounds, one client asking faster
 * than tiles render would hold every servlet thread, and the map would stop for everyone.
 */
class TileRenderPoolTest {

  private val callers = Executors.newCachedThreadPool()
  private val release = CountDownLatch(1)
  private var pool: TileRenderPool? = null

  @AfterEach
  fun stop() {
    release.countDown()
    callers.shutdownNow()
    pool?.close()
  }

  @Test
  fun `a master with every slot in flight is refused another render`() {
    val pool = pool(threads = 2, queueCapacity = 8, rendersPerMaster = 2)
    val started = CountDownLatch(2)
    repeat(2) { callers.submit { pool.render(MASTER) { started.countDown(); release.await() } } }
    assertTrue(started.await(5, TimeUnit.SECONDS))

    assertThrows<TileRenderPool.Unavailable> { pool.render(MASTER) { } }
  }

  @Test
  fun `a full queue refuses at once instead of growing`() {
    val pool = pool(threads = 1, queueCapacity = 1, rendersPerMaster = 10)
    val started = CountDownLatch(1)
    callers.submit { pool.render(MASTER) { started.countDown(); release.await() } }
    assertTrue(started.await(5, TimeUnit.SECONDS))
    callers.submit { pool.render(OTHER_MASTER) { } }
    waitUntil { pool.queuedRenders == 1 }

    assertThrows<TileRenderPool.Unavailable> { pool.render(THIRD_MASTER) { } }
  }

  @Test
  fun `a render that takes too long is answered as unavailable`() {
    val pool = pool(threads = 1, queueCapacity = 1, rendersPerMaster = 1, timeoutMillis = 100)

    assertThrows<TileRenderPool.Unavailable> { pool.render(MASTER) { release.await() } }
  }

  @Test
  fun `a render that timed out frees its slot`() {
    val pool = pool(threads = 2, queueCapacity = 1, rendersPerMaster = 1, timeoutMillis = 100)
    runCatching { pool.render(MASTER) { release.await() } }

    assertEquals(42, pool.render(MASTER) { 42 })
  }

  private fun pool(threads: Int, queueCapacity: Int, rendersPerMaster: Int, timeoutMillis: Long = 5_000): TileRenderPool {
    return TileRenderPool(threads, queueCapacity, rendersPerMaster, timeoutMillis).also { pool = it }
  }

  private fun waitUntil(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (!condition()) {
      check(System.nanoTime() < deadline) { "condition not met in time" }
      Thread.sleep(10)
    }
  }

  private companion object {
    const val MASTER = 1L
    const val OTHER_MASTER = 2L
    const val THIRD_MASTER = 3L
  }
}
