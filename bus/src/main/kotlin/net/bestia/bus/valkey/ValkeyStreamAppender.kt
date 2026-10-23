package net.bestia.bus.valkey

import io.lettuce.core.RedisClient
import io.lettuce.core.XAddArgs
import net.bestia.bus.BusJson
import java.util.concurrent.atomic.LongAdder

/**
 * Appends to one stream, waiting for Valkey to take the entry. Every entry carries the account it is about
 * beside its JSON, so a reader can replay one account's entries without decoding everyone's.
 *
 * The connection opens on the first append, and an append that could not open it tries again on the next one, so
 * a service that only appends now and then can start while Valkey is down.
 *
 * Counts its appends for whoever exports metrics: this module has no metrics dependency, so the counters are plain
 * and the exporter reads them when it is scraped.
 *
 * @param retained roughly how many entries the stream keeps; older ones are trimmed as new ones arrive
 */
class ValkeyStreamAppender<T : Any>(
  client: RedisClient,
  private val stream: String,
  private val accountOf: (T) -> Long,
  retained: Long = 10_000,
) : AutoCloseable {

  private val opened = lazy { client.connect() }
  private val connection by opened
  private val trim = XAddArgs.Builder.maxlen(retained).approximateTrimming()

  private val succeeded = LongAdder()
  private val failed = LongAdder()
  private val elapsedNanos = LongAdder()

  /** Appends Valkey took. */
  val appendsSucceeded: Long
    get() = succeeded.sum()

  /** Appends that threw: Valkey unreachable, silent past the command timeout, or the payload unwritable. */
  val appendsFailed: Long
    get() = failed.sum()

  /** Wall time spent in all appends, failed ones included - a timeout counts for its whole wait. */
  val appendNanos: Long
    get() = elapsedNanos.sum()

  /** The new entry's id. Throws when Valkey cannot be reached or does not answer in its command timeout. */
  fun append(payload: T): String {
    val startedAt = System.nanoTime()

    try {
      val fields = mapOf(
        ACCOUNT_FIELD to accountOf(payload).toString(),
        PAYLOAD_FIELD to BusJson.mapper.writeValueAsString(payload),
      )
      val id = connection.sync().xadd(stream, trim, fields)
      succeeded.increment()
      return id
    } catch (e: Exception) {
      failed.increment()
      throw e
    } finally {
      elapsedNanos.add(System.nanoTime() - startedAt)
    }
  }

  override fun close() {
    if (opened.isInitialized()) {
      connection.close()
    }
  }

  companion object {
    const val ACCOUNT_FIELD = "account"
    const val PAYLOAD_FIELD = "payload"
  }
}
