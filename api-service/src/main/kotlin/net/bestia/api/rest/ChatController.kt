package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Party and guild chat, written in the app. The line comes back over the socket like every other member's, so the
 * answer is empty. 403: Basic Skill is below the chat rank. 409: the master is in no such party or guild. A 504 means
 * the zone did not answer in time and the line may or may not have gone out: send it again with the same
 * Idempotency-Key.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/chat")
class ChatController(private val zone: ZoneClient) {

  /** [channel] is PARTY or GUILD; [text] at most 200 characters. */
  data class ChatOrder(val channel: String, val text: String)

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun send(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody order: ChatOrder,
  ) {
    val query = ZoneQuery.SendChat(masterId, order.channel, order.text)
    val sent = zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow<ZoneAnswer.ChatSent>()

    when (sent.refusal) {
      null -> {}
      ZoneAnswer.ChatSent.CHAT_LOCKED -> throw ResponseStatusException(HttpStatus.FORBIDDEN, sent.refusal)
      else -> throw ResponseStatusException(HttpStatus.CONFLICT, sent.refusal)
    }
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64
  }
}
