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

    val master = account.masterBestia
    requireNotNull(master) {
      "Master for account $account is not set"
    }

    return ClientInfoResponse(
        bestiaSlotCount = Account.NUM_BESTIA_SLOTS + account.additionalBestiaSlots,
        masterBestiaEntityId = master.entityId,
        ownedBestias = account.playerBestias.map {
          ClientInfoResponse.OwnedBestias(
              entityId = it.entityId,
              playerBestiaId = it.id
          )
        },
        activeEntityId = account.activeBestia?.entityId ?: 0
    )
  }
}