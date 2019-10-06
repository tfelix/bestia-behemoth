package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.model.geometry.Rect
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.routing.MessageApi
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
class ScriptRootApi(
    val scriptName: String,
    private val idGeneratorService: IdGenerator,
    private val mobFactory: MobFactory,
    private val entityCollisionService: EntityCollisionService
) {

  val commands = mutableListOf<EntityCommand>()

  fun info(text: String) {
    LOG.info { "${scriptName}: $text" }
  }

  fun debug(text: String) {
    LOG.debug { "${scriptName}: $text" }
  }

  fun findEntity(entityId: Long): EntityApi {
    require(entityId > 0L) { "Entity ID can not be null. This is probably a wrong call." }
    LOG.debug { "${scriptName}: findEntity($entityId)" }

    return EntityApi(entityId = entityId, commands = commands)
  }

  fun spawnMob(
      mobName: String,
      x: Long,
      y: Long,
      z: Long
  ): EntityApi {
    val position = Vec3(x, y, z)
    LOG.debug { "spawnMob: $mobName pos: $position" }
    val entity = mobFactory.build(mobName, position)
    commands.add(NewEntityCommand(entity))

    return EntityApi(entityId = entity.id, commands = commands)
  }

  fun findEntitiesBbox(x1: Long, y1: Long, z1: Long, x2: Long, y2: Long, z2: Long): Array<EntityApi> {
    LOG.debug { "${scriptName}: findEntitiesBbox($x1: Long, $y1: Long, $z1: Long, $x2: Long, $y2: Long, $z2: Long)" }

    val rect = Rect(x1, y1, z1, x2, y2, z2)
    val entities = entityCollisionService.getAllCollidingEntityIds(rect)

    return entities.map {
      EntityApi(entityId = it, commands = commands)
    }.toTypedArray()
  }

  fun newEntity(): EntityApi {
    val entityId = idGeneratorService.newId()
    commands.add(NewEntityCommand(Entity(entityId)))

    return EntityApi(entityId = entityId, commands = commands)
  }

  fun commitEntityUpdates(messageApi: MessageApi) {
    commands.map { messageApi.send(it) }.also {
      LOG.trace { "Send ${it.size} commands for exec $scriptName" }
    }
  }
}
