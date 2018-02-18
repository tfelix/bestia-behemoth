package net.bestia.messages

/**
 * Message coming from the client sometimes are redundant and thus need to me
 * marked to they can get routed the right way.
 * @Deprecated Use Envelope system.
 */
@Deprecated("Use Envelope System now.")
class FromClient(private val payload: AccountMessage) : AccountMessage(payload.accountId) {

  fun getPayload(): Any {
    return payload
  }

  override fun createNewInstance(accountId: Long): AccountMessage {
    return FromClient(payload.createNewInstance(accountId))
  }

  companion object {
    private const val serialVersionUID = 1L
  }
}
