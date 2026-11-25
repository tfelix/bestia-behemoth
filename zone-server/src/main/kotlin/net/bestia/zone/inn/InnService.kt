package net.bestia.zone.inn

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CoinPayments
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * What a master does at an inn: make it the save point, and pay for the bestias to be tended. See the inn section of
 * the settlements design page.
 */
@Service
class InnService(
  private val connections: ConnectionInfoService,
  private val masters: MasterRepository,
  private val transactions: TransactionTemplate,
  private val jobs: AsyncJobExecutor,
  private val worldView: WorldView,
  private val tending: InnTending,
  private val coins: CoinPayments,
  private val out: OutMessageProcessor,
) {

  fun open(accountId: AccountId, counter: InnCounter) {
    val masterId = connections.getMasterId(accountId)

    jobs.submit(masterId) { sendWindow(accountId, masterId, counter) }
  }

  fun makeSavePoint(accountId: AccountId, counter: InnCounter) {
    val masterId = connections.getMasterId(accountId)

    jobs.submit(masterId) {
      transactions.executeWithoutResult {
        val master = masters.findByIdOrThrow(masterId)
        master.spawnPosition = counter.door
        master.homeSettlementName = counter.settlementName
      }
      sendWindow(accountId, masterId, counter)
    }
  }

  /** On the tick: the bestias and the coins are both live state. Charges the price of now, if it is no dearer. */
  fun tend(world: World, accountId: AccountId, counter: InnCounter, expectedPrice: Int) {
    val masterId = connections.getMasterId(accountId)
    val lines = tending.quote(world, masterId)
    val price = lines.sumOf { it.price }

    when {
      price > expectedPrice -> refuse(accountId, OpError.INN_PRICE_CHANGED)
      coins.pay(world, connections.getSelectedMasterEntityId(accountId), masterId, price.toLong(), CoinSource.LODGING) ->
        tending.tend(world, lines, counter.door)
      else -> refuse(accountId, OpError.INN_CANNOT_AFFORD, listOf(price.toString()))
    }
    open(accountId, counter)
  }

  private fun sendWindow(accountId: AccountId, masterId: Long, counter: InnCounter) {
    val savePoint = transactions.execute { masters.findByIdOrThrow(masterId).spawnPosition }
    val lines = worldView.read { tending.quote(this, masterId) }

    out.sendToPlayer(accountId, InnSMSG(counter.entityId, counter.settlementName, savePoint == counter.door, lines))
  }

  private fun refuse(accountId: AccountId, code: OpError, args: List<String> = emptyList()) {
    out.sendToPlayer(accountId, OperationErrorSMSG(code, args))
  }
}
