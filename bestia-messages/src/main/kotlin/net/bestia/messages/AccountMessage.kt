package net.bestia.messages

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * These messages carry additional account information (the account id). Usually
 * they are coming from a client and are send towards a server. This account id
 * can be used to generate messages originating back to the client.
 *
 * @author Thomas Felix
 */
interface AccountMessage {
  @get:JsonProperty("aid")
  val accountId: Long
}
