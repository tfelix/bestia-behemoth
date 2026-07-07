package net.bestia.zone.ecs2.spring

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs2.World
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Optional single-threaded tick driver for an ecs2 [World], modelled on
 * `ZoneServer.start()`. Not a Spring bean by default so that merely having ecs2
 * on the classpath never starts a second simulation loop — construct and
 * [start] it explicitly when you want ecs2 to run.
 */
class Ecs2Runner(
  private val world: World,
  private val tickRate: Int = 20,
) {
  private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ecs2-tick") }

  @Volatile
  private var running = false

  fun start() {
    if (running) return
    running = true
    var lastTime = System.currentTimeMillis()

    executor.submit {
      LOG.info { "ecs2 tick loop started @ ${tickRate}Hz" }
      while (running) {
        val now = System.currentTimeMillis()
        val deltaTime = (now - lastTime) / 1000f
        lastTime = now

        try {
          world.tick(deltaTime)
        } catch (e: Exception) {
          LOG.error(e) { "Error in ecs2 tick: ${e.message}" }
        }

        val sleep = (1000L / tickRate) - (System.currentTimeMillis() - now)
        if (sleep > 0) Thread.sleep(sleep)
      }
    }
  }

  fun stop() {
    running = false
    executor.shutdown()
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
      executor.shutdownNow()
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
