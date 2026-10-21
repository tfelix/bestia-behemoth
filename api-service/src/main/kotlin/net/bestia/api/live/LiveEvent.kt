package net.bestia.api.live

import com.fasterxml.jackson.core.type.TypeReference
import net.bestia.bus.BusJson
import net.bestia.bus.StreamEntry
import net.bestia.bus.contract.ZoneEvent

/**
 * An event as the app receives it over the socket. [type] names it (`HomeFell`, `BestiaDiedAway`, ...) and [data]
 * holds its fields without the account, which is always the socket's own. [id] is what the app sends back as
 * `lastEventId` when it reconnects.
 */
data class LiveEvent(val id: String, val type: String, val data: Map<String, Any?>) {

  companion object {
    private val FIELDS = object : TypeReference<Map<String, Any?>>() {}

    fun of(streamed: StreamEntry<ZoneEvent>): LiveEvent {
      val fields = BusJson.mapper.convertValue(streamed.payload, FIELDS) - "accountId" - "type"
      return LiveEvent(streamed.id, streamed.payload::class.simpleName.orEmpty(), fields)
    }
  }
}
