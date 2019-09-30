package net.bestia.zoneserver

import net.bestia.zoneserver.config.ZoneserverNodeConfig

object Fixtures {
  val zoneserverNodeConfig = ZoneserverNodeConfig(
      nodeId = 1,
      serverName = "testserver",
      serverVersion = "1.0.0"
  )
}