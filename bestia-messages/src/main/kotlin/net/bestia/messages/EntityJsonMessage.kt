package net.bestia.messages

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Class can be send to a client and contains an entity id.
 *
 * @author Thomas Felix
 */
interface EntityMessage {
  @get:JsonProperty("eid")
  val entityId: Long
}
