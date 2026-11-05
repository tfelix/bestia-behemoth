package net.bestia.zone.postal.office

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.townsfolk.conversation.ServiceCounter
import net.bestia.zone.townsfolk.conversation.Speaker
import net.bestia.zone.util.AccountId
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * The postmaster's counter. A postmaster at work opens the post office window instead of a conversation, as a
 * merchant at work opens the shop. Called on the tick; the master's row is read and written in a job.
 */
@Service
class PostOfficeDesk(
  private val offices: PostOffices,
  private val clock: BestiaClock,
  private val masters: MasterRepository,
  private val connections: ConnectionInfoService,
  private val jobs: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
  private val out: OutMessageProcessor,
) : ServiceCounter {

  /** @return true when the window is on its way, false when this is not a postmaster at work. */
  override fun serve(accountId: AccountId, speaker: Speaker): Boolean {
    val office = officeAtWork(speaker) ?: return false
    val masterId = connections.getMasterId(accountId)

    jobs.submit(masterId) { answer(accountId, masterId, speaker, office, newHome = null) }

    return true
  }

  fun setHome(accountId: AccountId, postmaster: Speaker) {
    val office = officeAtWork(postmaster) ?: return
    val masterId = connections.getMasterId(accountId)

    jobs.submit(masterId) { answer(accountId, masterId, postmaster, office, newHome = office.settlement) }
  }

  private fun officeAtWork(postmaster: Speaker): PostOffices.Office? {
    if (postmaster.business != PostOffices.POST_OFFICE) return null
    if (!postmaster.merchant.hours.isOpenAt(clock.now().minuteOfDay)) return null

    return offices.officeOf(postmaster.settlement)
  }

  private fun answer(accountId: AccountId, masterId: Long, postmaster: Speaker, office: PostOffices.Office, newHome: Int?) {
    val home = transactions.execute {
      val master = masters.findByIdOrThrow(masterId)
      newHome?.let { master.homePostOffice = it }
      offices.homeOf(master.homePostOffice, master.homeSettlementName)
    }
    val homeName = home?.let { offices.officeOf(it)?.name }.orEmpty()

    out.sendToPlayer(
      accountId,
      PostOfficeOpenSMSG(postmaster.entityId, office.name, office.level, homeName, isHome = home == office.settlement)
    )
  }
}
