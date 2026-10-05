package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ZoneEngine
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.SettlementEconomyService
import net.bestia.zone.economy.WorldReserve
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

/**
 * Saves what changed since the last periodic save when the server stops: the tick is stopped first, so
 * nothing changes while the world is read, then the DB executor gets time to write it all.
 *
 * A [SmartLifecycle] rather than `@PreDestroy`, because lifecycle beans stop before any bean is destroyed,
 * so the executor and the repositories are all still there.
 */
@Component
class PersistOnShutdown(
  private val engine: ZoneEngine,
  private val world: WorldView,
  private val persistence: EntityPersistenceService,
  private val economy: SettlementEconomyService,
  private val reserve: WorldReserve,
  private val asyncJobExecutor: AsyncJobExecutor,
) : SmartLifecycle {

  @Volatile
  private var running = false

  override fun start() {
    running = true
  }

  override fun stop() {
    running = false
    LOG.info { "Saving the world before shutdown" }

    engine.stop()
    world.read {
      persistence.syncAll(this)
      economy.flush()
      reserve.flush()
    }
    asyncJobExecutor.shutdown(timeoutSeconds = DRAIN_SECONDS)

    LOG.info { "World saved, ${asyncJobExecutor.rejectedJobs} write(s) were dropped over the server's lifetime" }
  }

  override fun isRunning(): Boolean {
    return running
  }

  /** Stops early, before anything it relies on. */
  override fun getPhase(): Int {
    return Int.MAX_VALUE - 1
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
    const val DRAIN_SECONDS = 30L
  }
}
