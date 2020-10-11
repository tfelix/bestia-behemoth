package net.bestia.zoneserver.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Holds configuration variables for the server. These config variables are
 * either obtained by application.yml or via commandline attributes. They
 * are not meant to be changed during runtime.
 *
 * @author Thomas Felix
 */
@Component
data class ZoneserverNodeConfig(
    /**
     * Id of this server. Must be unique in the cluster.
     */
    @Value("\${zone.node-id}")
    val nodeId: Int
)