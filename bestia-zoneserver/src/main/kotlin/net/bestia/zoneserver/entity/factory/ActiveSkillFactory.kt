package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.model.battle.AttackRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.ScriptComponent
import net.bestia.zoneserver.entity.component.VisualComponent
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Spawns an entity which performs a running skill or attack.
 *
 * @author Thomas Felix
 */
@Component
class ActiveSkillFactory(
    idGenerator: IdGenerator,
    private val attackRepository: AttackRepository
) : EntityFactory(idGenerator) {

  fun build(attackId: Long, position: Vec3): Entity {
    val attack = attackRepository.findOneOrThrow(attackId)

    val entity = newEntity().apply {
      addComponent(PositionComponent(
          entityId = id,
          shape = position
      ))
      addComponent(VisualComponent(
          entityId = id,
          mesh = playerBestia.origin.mesh
      ))
      addComponent(ScriptComponent())
    }

    LOG.debug { "Build AttackEntity $entity" }

    return entity
  }
}
