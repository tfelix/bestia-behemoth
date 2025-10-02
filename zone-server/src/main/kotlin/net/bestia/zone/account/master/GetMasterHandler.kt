package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.Account
import net.bestia.zone.account.AccountRepository
import net.bestia.zone.account.findByIdOrThrow
import net.bestia.zone.account.master.GetMasterCMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.util.AccountId
import net.bestia.zone.ecs.session.ConnectionInfoService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetMasterHandler(
  private val accountRepository: AccountRepository,
  private val outMessageProcessor: OutMessageProcessor,
  private val connectionInfoService: ConnectionInfoService,
  private val bestiaInfoFactory: BestiaInfoFactory
) : InMessageProcessor.IncomingMessageHandler<GetMasterCMSG> {
  override val handles = GetMasterCMSG::class

  @Transactional(readOnly = true)
  override fun handle(msg: GetMasterCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val availableMasterInfo = getAvailableMaster(msg.playerId)

    outMessageProcessor.sendToPlayer(msg.playerId, availableMasterInfo)

    return true
  }

  private fun getAvailableMaster(accountId: AccountId): AvailableMasterSMSG {
    val account = accountRepository.findByIdOrThrow(accountId)

    // Group bestias by master ID (we need to get master ID from the connection info or bestia entities)
    // For now, we'll get bestias for each master individually
    val masterInfos = account.master.map { master ->
      // Get bestias specifically for this master
      val masterBestiaEntities = connectionInfoService.getOwnedEntitiesByMaster(accountId, master.id)
      val masterBestiaInfos = bestiaInfoFactory.getBestiaInfo(masterBestiaEntities)

      AvailableMasterSMSG.MasterInfo(
        id = master.id,
        name = master.name,
        level = master.level,
        hairColor = master.hairColor,
        skinColor = master.skinColor,
        hair = master.hair,
        face = master.face,
        body = master.body,
        position = master.position,
        bestias = masterBestiaInfos
      )
    }

    val maxMasterSlots = Account.DEFAULT_MASTER_SLOT_COUNT + account.additionalMasterSlots
    val maxBestiaSlots = Account.DEFAULT_BESTIA_SLOT_COUNT + account.additionalBestiaSlots

    return AvailableMasterSMSG(
      master = masterInfos,
      maxAvailableMasterSlots = maxMasterSlots,
      maxAvailableBestiaSlots = maxBestiaSlots
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
