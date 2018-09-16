package net.bestia.messages

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver

/**
 * Messages can be identified by returning a unique id.
 *
 * @author Thomas Felix
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "mid")
@JsonTypeIdResolver(MessageTypeIdResolver::class)
@JsonIgnoreProperties(ignoreUnknown = true)
interface MessageId {

  /**
   * Returns the id of this message. The same id is used on the client to
   * trigger events which have subscribed for the arrival of this kind of
   * messages.
   *
   * @return Event name to be triggered on the client.
   */
  @get:JsonIgnore
  val messageId: String
}