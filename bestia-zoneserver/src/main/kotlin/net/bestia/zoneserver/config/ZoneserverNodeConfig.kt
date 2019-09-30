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
     * Returns the name of this server.
     *
     * @return The server name.
     */
    @Value("server.name")
    val serverName: String,

    /**
     * Id of this server. Must be unique in the cluster.
     */
    @Value("server.node-id")
    val nodeId: Int,

    /**
     * Returns the server string of its version.
     *
     * @return The server version.
     */
    @Value("\${server.version}")
    val serverVersion: String
)

