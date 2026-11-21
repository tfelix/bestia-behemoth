package net.bestia.bus.valkey

import io.lettuce.core.LettuceFutures
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import net.bestia.bus.AppPresence
import net.bestia.bus.BusKeys
import java.time.Duration

class ValkeyAppPresence(client: RedisClient) : AppPresence, AutoCloseable {

  private val connection = client.connect()

  override fun markOpen(accountIds: Collection<Long>, ttlSeconds: Long) {
    if (accountIds.isEmpty()) {
      return
    }

    val async = connection.async()
    val writes = accountIds.map { async.set(BusKeys.appOpen(it), OPEN, SetArgs.Builder.ex(ttlSeconds)) }
    LettuceFutures.awaitAll(WRITE_TIMEOUT, *writes.toTypedArray())
  }

  override fun isOpen(accountId: Long): Boolean {
    return connection.sync().exists(BusKeys.appOpen(accountId)) > 0
  }

  override fun close() {
    connection.close()
  }

  private companion object {
    const val OPEN = "1"
    val WRITE_TIMEOUT: Duration = Duration.ofSeconds(5)
  }
}
