package net.bestia.bus.valkey

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.RedisClient
import net.bestia.bus.BusJson
import net.bestia.bus.BusKeys
import net.bestia.bus.WorldRebirth
import net.bestia.bus.contract.LastWorld

/**
 * Two keys, without an expiry: a zone that never comes back has no world to let players into. The connection opens
 * on first use, so a reader can start while Valkey is down.
 */
class ValkeyWorldRebirth(client: RedisClient) : WorldRebirth, AutoCloseable {

  private val opened = lazy { client.connect() }
  private val connection by opened

  override fun begin(lastWorld: LastWorld) {
    connection.sync().set(BusKeys.LAST_WORLD, BusJson.mapper.writeValueAsString(lastWorld))
    connection.sync().set(BusKeys.WORLD_STATE, REBIRTH)
  }

  override fun end() {
    connection.sync().del(BusKeys.WORLD_STATE)
  }

  override fun isUnderway(): Boolean {
    return connection.sync().get(BusKeys.WORLD_STATE) == REBIRTH
  }

  override fun lastWorld(): LastWorld? {
    val json = connection.sync().get(BusKeys.LAST_WORLD) ?: return null
    // One written by an older zone must not break the page that shows it.
    return runCatching { BusJson.mapper.readValue(json, LastWorld::class.java) }
      .onFailure { LOG.warn(it) { "Could not read the last world" } }
      .getOrNull()
  }

  override fun close() {
    if (opened.isInitialized()) {
      connection.close()
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val REBIRTH = "REBIRTH"
  }
}
