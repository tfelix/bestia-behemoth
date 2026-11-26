package net.bestia.bus.valkey

import io.lettuce.core.RedisClient
import net.bestia.bus.BusKeys
import net.bestia.bus.WorldRebirth

/**
 * One key, without an expiry: a zone that never comes back has no world to let players into. The connection opens on
 * first use, so a reader can start while Valkey is down.
 */
class ValkeyWorldRebirth(client: RedisClient) : WorldRebirth, AutoCloseable {

  private val opened = lazy { client.connect() }
  private val connection by opened

  override fun begin() {
    connection.sync().set(BusKeys.WORLD_STATE, REBIRTH)
  }

  override fun end() {
    connection.sync().del(BusKeys.WORLD_STATE)
  }

  override fun isUnderway(): Boolean {
    return connection.sync().get(BusKeys.WORLD_STATE) == REBIRTH
  }

  override fun close() {
    if (opened.isInitialized()) {
      connection.close()
    }
  }

  private companion object {
    const val REBIRTH = "REBIRTH"
  }
}
