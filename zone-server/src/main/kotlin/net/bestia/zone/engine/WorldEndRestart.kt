package net.bestia.zone.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.doomsday.WorldEndedEvent
import net.bestia.zone.message.ConnectionTerminator
import net.bestia.zone.message.OutMessageHandler
import net.bestia.zone.socket.ZoneReadinessService
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import kotlin.system.exitProcess

/**
 * Sends everyone away from an ended world and stops the zone. A world is only ever loaded at boot, so the next world
 * needs a fresh start: the container restarts the zone, and the boot sees the ended world and makes a new one.
 */
@Component
class WorldEndRestart(
  private val readiness: ZoneReadinessService,
  private val connections: OutMessageHandler,
  private val terminator: ConnectionTerminator,
  context: ConfigurableApplicationContext,
  /** Closing the context saves the world first, see [PersistOnShutdown]. */
  private val exit: (Int) -> Unit = { code -> exitProcess(SpringApplication.exit(context, ExitCodeGenerator { code })) },
) {

  @TransactionalEventListener(fallbackExecution = true)
  fun onWorldEnded(event: WorldEndedEvent) {
    readiness.refuseLogins()
    val accounts = connections.connectedAccountIds
    accounts.forEach { terminator.disconnect(it, DISCONNECT_REASON) }
    LOG.warn { "World ended ${event.ending}: sent ${accounts.size} account(s) away, stopping the zone" }

    // Off this thread: closing the context waits for the scheduler this event was published from.
    Thread({
      Thread.sleep(NOTICE_GRACE_MS)
      exit(EXIT_CODE)
    }, "world-end-exit").start()
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    const val DISCONNECT_REASON = "WORLD_ENDED"

    /** EX_TEMPFAIL: the zone stopped on purpose and comes back when started again. */
    const val EXIT_CODE = 75

    /** Lets the disconnect notices reach the clients before the sockets close. */
    const val NOTICE_GRACE_MS = 3_000L
  }
}
