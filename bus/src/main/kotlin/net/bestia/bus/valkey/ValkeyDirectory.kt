package net.bestia.bus.valkey

import io.lettuce.core.LettuceFutures
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import net.bestia.bus.BusKeys
import net.bestia.bus.Directory
import java.time.Duration

class ValkeyDirectory(client: RedisClient) : Directory, AutoCloseable {

  private val connection = client.connect()

  override fun register(accountIds: Collection<Long>, nodeId: String, ttlSeconds: Long) {
    if (accountIds.isEmpty()) {
      return
    }

    val async = connection.async()
    val writes = accountIds.map { async.set(BusKeys.directory(it), nodeId, SetArgs.Builder.ex(ttlSeconds)) }
    LettuceFutures.awaitAll(WRITE_TIMEOUT, *writes.toTypedArray())
  }

  override fun remove(accountId: Long) {
    connection.sync().del(BusKeys.directory(accountId))
  }

  override fun nodeOf(accountId: Long): String? {
    return connection.sync().get(BusKeys.directory(accountId))
  }

  override fun close() {
    connection.close()
  }

  private companion object {
    val WRITE_TIMEOUT: Duration = Duration.ofSeconds(5)
  }
}
