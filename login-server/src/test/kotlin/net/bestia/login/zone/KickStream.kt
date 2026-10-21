package net.bestia.login.zone

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import net.bestia.bus.BusKeys
import net.bestia.bus.contract.KickOrder
import net.bestia.bus.valkey.ValkeyStreamReader

/**
 * Reads the kick stream the way a zone does, as the `zone` user: the login server's own user may only append.
 * The kick is appended before the request that caused it answers, so a scenario reads it without waiting.
 */
class KickStream(host: String, port: Int) : AutoCloseable {

  private val client = RedisClient.create(
    RedisURI.Builder.redis(host, port).withAuthentication("zone", "zone-dev-password".toCharArray()).build()
  )
  private val reader = ValkeyStreamReader(client, BusKeys.KICKS, KickOrder::class.java)

  fun ordersFor(accountId: Long): List<KickOrder> {
    return reader.since("0-0", accountId).map { it.payload }
  }

  override fun close() {
    reader.close()
    client.shutdown()
  }
}
