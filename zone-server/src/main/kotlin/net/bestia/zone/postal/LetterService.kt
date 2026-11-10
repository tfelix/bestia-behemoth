package net.bestia.zone.postal

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.takeFromLive
import net.bestia.zone.postal.persistence.Letter
import net.bestia.zone.postal.persistence.LetterRepository
import net.bestia.zone.util.EntityId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

/**
 * Letters by pigeon: written to a master's name, instant, and kept for [KEPT_FOR] in the recipient's mailbox.
 * See the postal page of the design docs. Off the tick: every call reads or writes the database.
 */
@Service
class LetterService(
  private val letters: LetterRepository,
  private val masterRepository: MasterRepository,
  private val inventoryService: InventoryService,
  private val commodityItems: CommodityItems,
  private val world: WorldView,
  private val reserve: WorldReserve,
  private val events: ApplicationEventPublisher,
  private val transactions: TransactionTemplate,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** Who writes: a master, and its entity when it stands in the world, so the postage leaves its live bag too. */
  data class Sender(val masterId: Long, val liveEntityId: EntityId?)

  sealed interface Outcome {
    data class Sent(val recipientName: String) : Outcome
    data class Refused(val code: OpError, val args: List<String> = emptyList()) : Outcome
  }

  fun send(sender: Sender, recipientName: String, subject: String, body: String): Outcome {
    val coinId = commodityItems.coinItemId() ?: return Outcome.Refused(OpError.REQUEST_REFUSED)
    val letter = LetterText.of(subject, body) ?: return Outcome.Refused(OpError.REQUEST_REFUSED)

    val outcome = transactions.execute { status ->
      post(sender.masterId, recipientName.trim(), letter, coinId).also {
        if (it is Outcome.Refused) status.setRollbackOnly()
      }
    } ?: Outcome.Refused(OpError.REQUEST_REFUSED)

    if (outcome is Outcome.Sent) {
      // Past the commit. A live bag that disagrees is corrected by the next login; the database decided.
      sender.liveEntityId?.let { world.takeFromLive(it, ReservedItem.stack(coinId, POSTAGE, weight = 0)) }
      world.read { reserve.charge(-POSTAGE.toDouble(), CoinSource.POSTAGE) }
    }

    return outcome
  }

  /** A letter from the post itself, such as a notice that a home office closed. Free, and never turned back. */
  @Transactional
  fun postNotice(recipientMasterId: Long, senderName: String, subject: String, body: String) {
    val recipient = masterRepository.findByIdOrThrow(recipientMasterId)
    val now = clock.instant()
    val letter = letters.save(
      Letter(
        recipientMasterId = recipient.id,
        senderMasterId = NO_SENDER,
        senderName = senderName,
        subject = subject,
        body = body,
        sentAt = now,
        expiresAt = now.plus(KEPT_FOR),
      )
    )
    events.publishEvent(LetterArrivedEvent(recipient.account.id, recipient.id, LetterView.of(letter)))
  }

  @Transactional(readOnly = true)
  fun mailbox(masterId: Long): List<LetterView> {
    return letters.findByRecipientMasterIdAndExpiresAtAfterOrderBySentAtDesc(masterId, clock.instant())
      .map(LetterView::of)
  }

  @Transactional
  fun markRead(masterId: Long, letterId: Long) {
    letters.findByIdAndRecipientMasterId(letterId, masterId)?.read = true
  }

  @Transactional
  fun delete(masterId: Long, letterId: Long) {
    letters.findByIdAndRecipientMasterId(letterId, masterId)?.let(letters::delete)
  }

  /** The mailbox read already hides expired letters; this only gives the space back. */
  @Scheduled(fixedDelay = 6 * 60 * 60 * 1000L)
  @Transactional
  fun deleteExpired() {
    letters.deleteExpired(clock.instant())
  }

  private fun post(senderMasterId: Long, recipientName: String, text: LetterText, coinId: Long): Outcome {
    val now = clock.instant()
    val recipient = masterRepository.findByName(recipientName)
      ?: return Outcome.Refused(OpError.MAIL_RECIPIENT_UNKNOWN, listOf(recipientName))

    if (letters.countByRecipientMasterIdAndExpiresAtAfter(recipient.id, now) >= MAX_LETTERS) {
      return Outcome.Refused(OpError.MAIL_RECIPIENT_FULL, listOf(recipient.name))
    }

    if (!inventoryService.consumeAll(senderMasterId, listOf(coinId to POSTAGE))) {
      return Outcome.Refused(OpError.MAIL_CANNOT_AFFORD)
    }

    val letter = letters.save(
      Letter(
        recipientMasterId = recipient.id,
        senderMasterId = senderMasterId,
        senderName = masterRepository.findByIdOrThrow(senderMasterId).name,
        subject = text.subject,
        body = text.body,
        sentAt = now,
        expiresAt = now.plus(KEPT_FOR),
      )
    )
    events.publishEvent(LetterArrivedEvent(recipient.account.id, recipient.id, LetterView.of(letter)))

    return Outcome.Sent(recipient.name)
  }

  companion object {
    /** In gold. Enough that nobody fills a mailbox with spam. */
    const val POSTAGE = 1

    val KEPT_FOR: Duration = Duration.ofDays(30)

    /** A pigeon finds a full mailbox and turns back. Keeps one master from being buried in letters. */
    const val MAX_LETTERS = 100

    /** The sender id of a letter the post writes itself. */
    const val NO_SENDER = 0L
  }
}
