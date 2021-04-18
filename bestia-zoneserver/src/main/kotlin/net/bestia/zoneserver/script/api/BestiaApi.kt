package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.model.geometry.Shape
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.EntityRequestService
import net.bestia.zoneserver.actor.entity.NewEntity
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory

private val LOG = KotlinLogging.logger { }

/**
 * Global script API used by all scripts in the Bestia system to interact with
 * the Behemoth server.
 *
 * @author Thomas Felix
 */
class BestiaApi(
    val scriptName: String,
    private val idGeneratorService: IdGenerator,
    private val mobFactory: MobFactory,
    private val entityCollisionService: EntityCollisionService,
    private val entityRequestService: EntityRequestService
) {

  val commands = mutableListOf<EntityMessage>()

  fun info(text: Any) {
    LOG.info { "${scriptName}: $text" }
  }

  fun debug(text: Any) {
    LOG.debug { "${scriptName}: $text" }
  }

  fun findEntity(entityId: Long): EntityApi {
    LOG.trace { "${scriptName}: findEntity($entityId)" }
    require(entityId > 0L) { "Entity ID can not be null" }

    return EntityApi(
        entityId = entityId,
        commands = commands,
        scriptName = scriptName,
        entityRequestService = entityRequestService
    )
  }

  fun spawnMob(
      mobName: String,
      pos: Vec3
  ): EntityApi {
    LOG.trace { "spawnMob: $mobName pos: $pos" }
    require(pos.x > 0L) { "X must be greater then 0" }
    require(pos.y > 0L) { "Y must be greater then 0" }
    require(pos.z > 0L) { "Z must be greater then 0" }

    val entity = mobFactory.build(mobName, pos)
    commands.add(NewEntity(entity))

    return EntityApi(
        entityId = entity.id,
        commands = commands,
        scriptName = scriptName,
        entityRequestService = entityRequestService
    )
  }

  fun findEntities(shape: Shape): List<EntityApi> {
    LOG.trace { "${scriptName}: findEntities($shape)" }

    val entities = entityCollisionService.getAllCollidingEntityIds(shape)

    return entities.map {
      EntityApi(entityId = it, commands = commands, scriptName = scriptName, entityRequestService = entityRequestService)
    }
  }

  fun newEntity(): EntityApi {
    LOG.trace { "${scriptName}: newEntity" }

    val entityId = idGeneratorService.newId()
    commands.add(NewEntity(Entity(entityId)))

    return EntityApi(entityId = entityId, commands = commands, scriptName = scriptName, entityRequestService = entityRequestService)
  }

  override fun toString(): String {
    return this::class.java.simpleName
  }
}
