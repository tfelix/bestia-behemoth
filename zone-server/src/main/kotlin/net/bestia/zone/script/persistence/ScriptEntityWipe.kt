package net.bestia.zone.script.persistence

import net.bestia.zone.persistence.PersistedEntityRepository
import net.bestia.zone.persistence.deleteAllByKind
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.world.WorldScopedData
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Script entities stand at places of the world they were written for. */
@Component
@Order(5)
class ScriptEntityWipe(
  private val persistedEntityRepository: PersistedEntityRepository,
) : WorldScopedData {

  override fun wipe() {
    persistedEntityRepository.deleteAllByKind(ScriptComponent.KIND)
  }
}
