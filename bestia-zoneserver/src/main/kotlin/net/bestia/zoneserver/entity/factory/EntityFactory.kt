package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityService

interface EntityBuilder {
  fun build(entity: Entity)
}

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

  fun buildEntity(builder: EntityBuilder) {
    LOG.trace { "Creating entity with: $builder" }

    // TODO Possibly we need to somehow cache the component ids if they should be unique.
    val e = entityService.newEntity()
    builder.build(e)

    val entityEnvelope = EntityEnvelope(e.id, e)
    messageApi.send(entityEnvelope)
  }
}
