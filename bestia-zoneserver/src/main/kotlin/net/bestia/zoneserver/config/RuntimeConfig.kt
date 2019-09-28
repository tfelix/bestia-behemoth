package net.bestia.zoneserver.config

import net.bestia.model.server.MaintenanceLevel
import org.springframework.stereotype.Service
import java.lang.IllegalArgumentException

/**
 * This configuration service holds information about the current state of the
 * server cluster while they are running. These information might get changed during
 * runtime.
 *
 * @author Thomas Felix
 */
data class RuntimeConfig(
    val maintenanceLevel: MaintenanceLevel = MaintenanceLevel.NONE
)
