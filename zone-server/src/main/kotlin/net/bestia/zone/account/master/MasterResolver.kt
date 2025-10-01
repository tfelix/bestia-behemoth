package net.bestia.zone.account.master

import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Helper class to resolve all sorts of master queries.
 */
@Component
class MasterResolver(
  private val connectionInfoService: ConnectionInfoService,
  private val masterRepository: MasterRepository,
) {

  fun getSelectedMasterByAccountId(accountId: Long): Master {
    try {
      val masterId = connectionInfoService.getMasterId(accountId)

      return masterRepository.findByIdOrThrow(masterId)
    } catch (e: Exception) {
      throw MasterNotFoundException(e)
    }
  }
  
  fun getSelectedMasterEntityIdByAccountId(accountId: AccountId): EntityId? {
    return connectionInfoService.getSelectedMasterEntityId(accountId)
  }

  @Transactional(readOnly = true)
  fun getAccountIdByMasterName(masterName: String): Long {
    return masterRepository.findByName(masterName)?.account?.id
      ?: throw MasterNotFoundException()
  }

  @Transactional(readOnly = true)
  fun getEntityIdByMasterId(masterId: Long): EntityId? {
    val master = masterRepository.findByIdOrNull(masterId)
      ?: return null

    return connectionInfoService.getSelectedMasterEntityId(master.account.id)
  }
}