package net.bestia.zone.account.master.persistence

import net.bestia.zone.account.master.EntityPersistenceService
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterNotFoundException
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.ecs.persistence.ComponentEntityWriter
import org.springframework.stereotype.Service

@Service
class MasterEntityPersistenceService(
  writers: List<ComponentEntityWriter<*, Master>>,
  private val masterRepository: MasterRepository
) : EntityPersistenceService<Master>(writers) {

  /**
   * Pessimistic-locking read: this is a read-modify-write against mutable master state that can
   * be reached by more than one concurrently-running caller for the same master id (see
   * [MasterRepository.findByIdForUpdate]).
   */
  override fun loadEntity(entityId: Long): Master {
    return masterRepository.findByIdForUpdate(entityId) ?: throw MasterNotFoundException()
  }

  override fun saveEntity(entity: Master) {
    masterRepository.save(entity)
  }
}