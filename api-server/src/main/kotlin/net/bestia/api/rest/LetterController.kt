package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneAnswer.LetterSent
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * A master's letters by pigeon. Not cached: a letter read or thrown away in the game sends no event that would
 * clear a cached mailbox.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/letters")
class LetterController(private val zone: ZoneClient) {

  data class Letter(
    val letterId: Long,
    val senderName: String,
    val subject: String,
    val body: String,
    val sentAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val read: Boolean,
  )

  /** [subject] at most 60 characters, [body] at most 1000; one of them must not be empty. */
  data class LetterOrder(val recipientName: String, val subject: String, val body: String)

  /** Newest first. */
  @GetMapping
  fun list(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): List<Letter> {
    val mailbox = zone.ask(accountId, ZoneQuery.Letters(masterId)).answerOrThrow<ZoneAnswer.Letters>()

    return mailbox.letters.map {
      Letter(it.letterId, it.senderName, it.subject, it.body, it.sentAtEpochMs, it.expiresAtEpochMs, it.read)
    }
  }

  /**
   * Sends a letter, which costs the master 1 gold. 403: Basic Skill is below the chat rank. 422: no master has that
   * name. 409: the recipient's mailbox is full. 402: the master cannot pay the postage. A 504 means the zone did not
   * answer in time and the letter may or may not have gone: send it again with the same Idempotency-Key.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun send(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody order: LetterOrder,
  ) {
    val query = ZoneQuery.SendLetter(masterId, order.recipientName, order.subject, order.body)
    val sent = zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow<LetterSent>()

    val refused = when (sent.refusal) {
      null -> return
      LetterSent.CHAT_LOCKED -> HttpStatus.FORBIDDEN
      LetterSent.RECIPIENT_UNKNOWN -> HttpStatus.UNPROCESSABLE_ENTITY
      LetterSent.RECIPIENT_FULL -> HttpStatus.CONFLICT
      else -> HttpStatus.PAYMENT_REQUIRED
    }
    throw ResponseStatusException(refused, sent.refusal)
  }

  @PutMapping("/{letterId}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun markRead(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable letterId: Long,
  ) {
    zone.ask(accountId, ZoneQuery.ReadLetter(masterId, letterId)).answerOrThrow<ZoneAnswer.Done>()
  }

  @DeleteMapping("/{letterId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun delete(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable letterId: Long,
  ) {
    zone.ask(accountId, ZoneQuery.DeleteLetter(masterId, letterId)).answerOrThrow<ZoneAnswer.Done>()
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64
  }
}
