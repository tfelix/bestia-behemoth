package net.bestia.zone.companion.query

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneAnswer.LetterSent
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.postal.LetterService
import net.bestia.zone.postal.LetterView
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Service

/**
 * Letters read and written in the companion app, by the same rules as in the game. A send runs in the account's
 * inbox, because the postage may leave the bag of a master that is being played.
 */
@Service
class LetterCommands(
  private val owned: OwnedMasters,
  private val basicSkill: MasterBasicSkill,
  private val letterService: LetterService,
  private val inbox: AccountTaskExecutor,
  private val sessions: ConnectionInfoService,
) {

  fun list(correlationId: String, accountId: Long, query: ZoneQuery.Letters): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    return BusReply.ok(correlationId, ZoneAnswer.Letters(letterService.mailbox(query.masterId).map(::toAnswer)))
  }

  fun send(correlationId: String, accountId: Long, query: ZoneQuery.SendLetter): BusReply {
    var reply = BusReply.of(correlationId, Status.NOT_FOUND)
    inbox.onIo(accountId) {
      // Never let this throw: a failure in an account's inbox closes that account's game connection.
      reply = try {
        sendOwned(correlationId, accountId, query)
      } catch (e: Exception) {
        LOG.error(e) { "Letter of master ${query.masterId} of account $accountId failed" }
        BusReply.of(correlationId, Status.BUSY, "internal error")
      }
    }.join()

    return reply
  }

  fun read(correlationId: String, accountId: Long, query: ZoneQuery.ReadLetter): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    letterService.markRead(query.masterId, query.letterId)

    return BusReply.ok(correlationId, ZoneAnswer.Done)
  }

  fun delete(correlationId: String, accountId: Long, query: ZoneQuery.DeleteLetter): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    letterService.delete(query.masterId, query.letterId)

    return BusReply.ok(correlationId, ZoneAnswer.Done)
  }

  private fun sendOwned(correlationId: String, accountId: Long, query: ZoneQuery.SendLetter): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)

    if (!basicSkill.mayChat(accountId, query.masterId)) {
      return BusReply.ok(correlationId, LetterSent(LetterSent.CHAT_LOCKED))
    }

    val liveEntityId = if (sessions.isPlaying(accountId, query.masterId)) {
      sessions.getSelectedMasterEntityId(accountId)
    } else {
      null
    }
    val sender = LetterService.Sender(query.masterId, liveEntityId)

    return when (val outcome = letterService.send(sender, query.recipientName, query.subject, query.body)) {
      is LetterService.Outcome.Sent -> BusReply.ok(correlationId, LetterSent(null))
      is LetterService.Outcome.Refused -> when (outcome.code) {
        OpError.MAIL_RECIPIENT_UNKNOWN -> BusReply.ok(correlationId, LetterSent(LetterSent.RECIPIENT_UNKNOWN))
        OpError.MAIL_RECIPIENT_FULL -> BusReply.ok(correlationId, LetterSent(LetterSent.RECIPIENT_FULL))
        OpError.MAIL_CANNOT_AFFORD -> BusReply.ok(correlationId, LetterSent(LetterSent.CANNOT_AFFORD))
        else -> BusReply.of(correlationId, Status.INVALID, "A letter needs a subject or a body, within the form's limits")
      }
    }
  }

  private fun toAnswer(letter: LetterView): ZoneAnswer.Letters.Letter {
    return ZoneAnswer.Letters.Letter(
      letterId = letter.letterId,
      senderName = letter.senderName,
      subject = letter.subject,
      body = letter.body,
      sentAtEpochMs = letter.sentAt.toEpochMilli(),
      expiresAtEpochMs = letter.expiresAt.toEpochMilli(),
      read = letter.read,
    )
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
