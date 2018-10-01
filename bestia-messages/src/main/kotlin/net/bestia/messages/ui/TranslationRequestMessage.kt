package net.bestia.messages.ui

import com.fasterxml.jackson.annotation.JsonProperty
import net.bestia.messages.AccountMessage
import net.bestia.messages.MessageId

/**
 * This message requests a translation from the server. The data is fetched via
 * our I18N interface and then delivered to the client with an translation
 * response message.
 *
 * @author Thomas Felix
 */
data class TranslationRequestMessage(
    override val accountId: Long,

    @JsonProperty("is")
    val items: List<TranslationItem>,

    /**
     * The token is put in the answer of this message. Synce these requests are
     * async the token can be used by the client to identify the answers of the
     * request.
     */
    @JsonProperty("t")
    val token: String
) : AccountMessage, MessageId {

  override val messageId: String
    get() = MESSAGE_ID

  companion object {
    const val MESSAGE_ID = "translation.request"
  }
}
