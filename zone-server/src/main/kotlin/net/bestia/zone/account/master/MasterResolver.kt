package net.bestia.zone.account.master

import com.github.quillraven.fleks.Entity
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.EntityId
import net.bestia.zone.util.MasterEntityId
import net.bestia.zone.ecs.EntityRegistry
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
  private val entityRegistry: EntityRegistry,
) {

  fun getSelectedMasterByAccountId(accountId: Long): Master {
    try {
      val masterId = connectionInfoService.getMasterId(accountId)

      return masterRepository.findByIdOrThrow(masterId)
    } catch (e: Exception) {
      throw MasterNotFoundException(e)
    }
  }
  
  fun getSelectedMasterEntityByAccountId(accountId: AccountId): Entity? {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

    return entityRegistry.getEntity(masterEntityId)
  }

  @Transactional(readOnly = true)
  fun getAccountIdByMasterName(masterName: String): Long {
    return masterRepository.findByName(masterName)?.account?.id
      ?: throw MasterNotFoundException()
  }

  fun getEntityByMasterEntityId(masterEntityId: MasterEntityId): Entity? {
    return entityRegistry.getEntity(masterEntityId)
  }

  @Transactional(readOnly = true)
  fun getEntityIdByMasterId(masterId: Long): EntityId? {
    val master = masterRepository.findByIdOrNull(masterId)
      ?: return null

    return connectionInfoService.getSelectedMasterEntityId(master.account.id)
  }

  @Transactional(readOnly = true)
  fun getEntityByMasterId(masterId: Long): Entity? {
    val master = masterRepository.findByIdOrNull(masterId)
      ?: return null
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(master.account.id)

    return entityRegistry.getEntity(masterEntityId)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}