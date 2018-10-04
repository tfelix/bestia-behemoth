package net.bestia.zoneserver.entity.factory

import mu.KotlinLogging
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityService
import java.lang.IllegalArgumentException

private val LOG = KotlinLogging.logger { }

/**
 * The EcsEntityFactory is responsible for translating ecs blueprints into
 * working entities with all components attached and initialized.
 *
 * @author Thomas Felix
 */
@org.springframework.stereotype.Component
class EntityFactory(
    private val entityService: EntityService,
    private val messageApi: MessageApi,
    factories: List<AbstractFactory<*>>
) {

  private val blueprintEntityFactories = factories.map { it.supportsType to it }.toMap()

  fun <T : Blueprint> build(blueprint: T): Entity {
    LOG.trace { "Creating entity with: $blueprint" }

    val suitableFactory = blueprintEntityFactories[blueprint::class.java]
        ?: throw IllegalArgumentException("No suitable factory for blueprint $blueprint found.")

    val e = entityService.newEntity()
    suitableFactory.build(e, blueprint)

    val entityEnvelope = EntityEnvelope(e.id, e)
    messageApi.send(entityEnvelope)

    return e
  }
}
