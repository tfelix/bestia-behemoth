package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.EntityService

private val LOG = KotlinLogging.logger { }

/**
 * The EcsEntityFactory is responsible for translating ecs blueprints into
 * working entities with all components attached and initialized.
 *
 * @author Thomas Felix
 */
@org.springframework.stereotype.Component
internal class EntityFactory(
    private val entityService: EntityService,
    private val messageApi: MessageApi
) {

  fun buildEntity(blueprint: EntityBlueprint) {
    LOG.trace { "Creating entity with: $blueprint" }

    val e = entityService.newEntity()
    val entityEnvelope = EntityEnvelope(e.id, e)
    messageApi.send(entityEnvelope)

    blueprint.components.forEach {
      entityService.updateComponent(it)
    }
  }
}
