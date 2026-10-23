package net.bestia.bus

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneQuery
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * The api-server's side of request/reply: ask the zone that holds an account, and wait for its answer.
 *
 * The future fails with [ZoneUnavailableException] when no zone is listening, and with a
 * [java.util.concurrent.TimeoutException] when the zone did not answer in time. A write that timed out may or
 * may not have happened; resend it with the same idempotency key.
 */
interface NodeBus {

  fun ask(
    accountId: Long,
    query: ZoneQuery,
    idempotencyKey: String? = null,
    timeout: Duration = DEFAULT_TIMEOUT,
  ): CompletableFuture<BusReply>

  companion object {
    val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(5)
  }
}
