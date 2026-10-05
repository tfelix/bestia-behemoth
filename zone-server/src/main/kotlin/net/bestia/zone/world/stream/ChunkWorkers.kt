package net.bestia.zone.world.stream

import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs terrain work - generating, encoding, compressing - off the tick thread, and hands each result back to
 * the tick in [drain]. Results come back through this queue rather than `WorldView.post`, because the
 * streaming beans are dependencies of systems and so cannot depend on the world themselves.
 *
 * With no workers configured it does the work at once, on the caller.
 */
@Component
class ChunkWorkers internal constructor(private val pool: Executor?) {

  @Autowired
  constructor(settings: ChunkStreamConfig) : this(poolFor(settings))

  private val delivered = ConcurrentLinkedQueue<() -> Unit>()

  /** Runs [work] off the tick; [deliver] gets its result on the tick, in [drain]. Without workers, both now. */
  fun <T> submit(work: () -> T, deliver: (Result<T>) -> Unit) {
    val pool = pool
    if (pool == null) {
      deliver(runCatching(work))
      return
    }

    pool.execute {
      val result = runCatching(work)
      delivered.add { deliver(result) }
    }
  }

  /** Hands finished work back to whoever asked for it. Tick thread only. */
  fun drain() {
    while (true) {
      val delivery = delivered.poll() ?: break
      delivery()
    }
  }

  @PreDestroy
  fun shutdown() {
    val service = pool as? ExecutorService ?: return
    service.shutdownNow()
    service.awaitTermination(5, TimeUnit.SECONDS)
  }

  private companion object {
    private val threads = AtomicInteger()

    fun poolFor(settings: ChunkStreamConfig): ExecutorService? {
      if (settings.encodeWorkers <= 0) return null

      return Executors.newFixedThreadPool(settings.encodeWorkers) { r ->
        Thread(r, "zone-chunk-worker-${threads.getAndIncrement()}").apply { isDaemon = true }
      }
    }
  }
}
