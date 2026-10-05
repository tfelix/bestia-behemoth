package net.bestia.zone.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import org.hibernate.resource.jdbc.spi.StatementInspector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Flags SQL that runs on the tick thread, where one database round trip stalls the whole world.
 *
 * Hibernate shows it every statement, including lazy loads behind a service call, which a rule over class
 * dependencies cannot see. [failOnTick] turns the warning into an exception, for tests.
 */
class TickSqlGuard(private val failOnTick: Boolean) : StatementInspector {

  private val reported = ConcurrentHashMap.newKeySet<String>()

  override fun inspect(sql: String): String {
    if (Thread.currentThread().name != ZoneEngine.TICK_THREAD_NAME) {
      return sql
    }

    violations.incrementAndGet()
    if (failOnTick) {
      throw IllegalStateException("SQL on the tick thread: $sql")
    }
    if (reported.add(sql)) {
      LOG.warn(IllegalStateException("SQL on the tick thread")) { "The tick ran a database statement: $sql" }
    }

    return sql
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Every statement seen on the tick since start, for tests to assert on. */
    val violations = AtomicInteger()
  }
}
