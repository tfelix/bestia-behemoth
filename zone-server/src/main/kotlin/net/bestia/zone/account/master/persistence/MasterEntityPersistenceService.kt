package net.bestia.zone.account.master.persistence

import net.bestia.zone.account.master.EntityPersistenceService
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.persistence.ComponentEntityWriter
import org.springframework.stereotype.Service

@Service
class MasterEntityPersistenceService(
  worldView: WorldView,
  writers: List<ComponentEntityWriter<*, Master>>,
  private val masterRepository: MasterRepository
) : EntityPersistenceService<Master>(worldView, writers) {
  override fun loadEntity(entityId: Long): Master {
    return masterRepository.findByIdOrThrow(entityId)
  }

  override fun saveEntity(entity: Master) {
    masterRepository.save(entity)
  }
}