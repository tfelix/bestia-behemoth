package net.bestia.zone.postal.office

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.LetterService
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.settlement.TownFellEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * A town whose sign fell loses its post office at once, though the building still stands: the docs' "Losing an
 * Office". Its mail goes on through the nearest other office with its promise kept, and its residents get that
 * office as their home and a pigeon to say so.
 */
@Component
class PlayerOfficeClosure(
  private val offices: PostOffices,
  private val masters: MasterRepository,
  private val parcels: ParcelRepository,
  private val letters: LetterService,
  private val jobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
) {

  /** On the tick, which is where [PostOffices] may be asked; the rows are moved in a job. */
  @EventListener
  fun onFell(event: TownFellEvent) {
    val lost = PostOffices.PLAYER_OFFICE_BASE + event.townId.toInt()
    val successor = offices.successorOf(lost) ?: return
    val successorName = offices.officeOf(successor)?.name ?: return

    jobs.submit(lost) { move(lost, successor, event.name, successorName) }
  }

  private fun move(lost: Int, successor: Int, lostName: String, successorName: String) {
    val residents = transactions.execute {
      for (parcel in parcels.findByOriginOfficeOrDestinationOffice(lost, lost)) {
        if (parcel.originOffice == lost && parcel.sackInstanceId == null) parcel.originOffice = successor
        if (parcel.destinationOffice == lost) parcel.destinationOffice = successor
      }

      masters.findAllByHomePostOffice(lost).onEach { it.homePostOffice = successor }.map { it.id }
    }.orEmpty()

    for (masterId in residents) {
      letters.postNotice(
        masterId,
        SENDER_NAME,
        "Your post office in $lostName is closed",
        "The sign of $lostName has fallen, and its post office with it. Your mail now goes to $successorName.",
      )
    }
    LOG.info { "Office $lost closed; ${residents.size} resident(s) and its mail moved to $successor" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val SENDER_NAME = "The Post"
  }
}
