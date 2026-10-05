package net.bestia.zone.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.WorldOwnership
import org.hibernate.resource.jdbc.spi.StatementInspector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Flags SQL that runs on the tick thread, or on a thread that holds the world on a lease, where one
 * database round trip stalls the whole world.
 *
 * Hibernate shows it every statement, including lazy loads behind a service call, which a rule over class
 * dependencies cannot see. [failOnTick] turns the warning into an exception, for tests.
 */
class TickSqlGuard(private val failOnTick: Boolean) : StatementInspector {

  private val reported = ConcurrentHashMap.newKeySet<String>()

  override fun inspect(sql: String): String {
    val onTick = Thread.currentThread().name == ZoneEngine.TICK_THREAD_NAME
    if (!onTick && !WorldOwnership.isInsideLease()) {
      return sql
    }

    val where = if (onTick) "the tick thread" else "a world lease"
    violations.incrementAndGet()
    lastViolation = "$where: $sql"
    if (failOnTick) {
      throw IllegalStateException("SQL on $where: $sql")
    }
    if (reported.add(sql)) {
      LOG.warn(IllegalStateException("SQL on $where")) { "A database statement ran on $where: $sql" }
    }

    return sql
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Every statement seen on the tick since start, for tests to assert on. */
    val violations = AtomicInteger()

    @Volatile
    var lastViolation: String? = null
      private set
  }
}
