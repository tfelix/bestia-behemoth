package net.bestia.zoneserver.actor.client

import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import org.springframework.stereotype.Service
import javax.transaction.Transactional

@Service
@Transactional
class ClientInfoService(
    private val accountRepository: AccountRepository
) {

  fun getClientInfo(accountId: Long): ClientInfoResponse {
    val account = accountRepository.findOneOrThrow(accountId)

    return ClientInfoResponse(
        bestiaSlotCount = Account.NUM_BESTIA_SLOTS + account.additionalBestiaSlots,
        masterBestiaEntityId = account.masterBestia!!.entityId,
        ownedBestiaEntityIds = account.playerBestias.map { it.entityId }
    )
  }
}