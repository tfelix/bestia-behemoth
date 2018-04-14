package net.bestia.zoneserver.client

import com.hazelcast.core.HazelcastInstance
import com.hazelcast.core.ISet
import org.springframework.stereotype.Service

@Service
class ConnectionService(
        hz: HazelcastInstance
) {
  private val connections: ISet<Long> = hz.getSet("connections")

  fun addConnection(accountId: Long) {
    connections.add(accountId)
  }

  fun removeConnection(accountId: Long) {
    connections.remove(accountId)
  }

  fun iterateOverConnections(handler: (Long) -> Unit, filter: (Long) -> Boolean = this::alwaysTrue) {
    connections.iterator().forEachRemaining {
      if(filter(it)) {
        handler(it)
      }
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun alwaysTrue(x: Long): Boolean{
    return true
  }
}