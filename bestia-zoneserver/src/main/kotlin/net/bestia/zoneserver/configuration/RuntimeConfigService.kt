package net.bestia.zoneserver.configuration

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.IMap
import net.bestia.model.server.MaintenanceLevel
import org.springframework.stereotype.Service

/**
 * This configuration service holds information about the current state of the
 * server while they are running. These information might get changed during
 * runtime. It is saved via the in memory database.
 *
 * @author Thomas Felix
 */
@Service
class RuntimeConfigService(
        hz: HazelcastInstance
) {

  private val config: IMap<String, Any> = hz.getMap("server.config")

  /**
   * Returns the flag if the server is in maintenance mode.
   *
   * @return TRUE if the server is in maintenance mode. FALSE otherwise.
   */
  /**
   * Sets the flag if the server is in maintenance mode.
   *
   * @param flag
   * The flag to set the server into maintenance mode.
   */
  var maintenanceMode: MaintenanceLevel
    get() = config.getOrDefault("serverMaintenanceMode", MaintenanceLevel.NONE) as MaintenanceLevel
    set(flag) = config.set("serverMaintenanceMode", flag)
}
