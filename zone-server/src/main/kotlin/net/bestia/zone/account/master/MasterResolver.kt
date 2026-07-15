package net.bestia.zone.account.master

import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.NoActiveSessionException
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
    // Honour the nullable contract: a player with no active session (e.g. one that already logged
    // out its master but kept the socket open) has no selected master entity, rather than being an
    // error. Callers such as the disconnect handler rely on getting null here.
    return try {
      connectionInfoService.getSelectedMasterEntityId(accountId)
    } catch (_: NoActiveSessionException) {
      null
    }
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