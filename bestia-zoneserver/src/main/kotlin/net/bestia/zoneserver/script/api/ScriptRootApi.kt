package net.bestia.zoneserver.script.api

import akka.actor.ActorContext
import mu.KotlinLogging
import net.bestia.model.geometry.Rect
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val LOG = KotlinLogging.logger { }

/**
 * Does a blocking lookup of entities. This should only be used in conjunction with scripts
 * as its unavoidable that scripts need an entity in order to perform certain computations.
 */
@Service
class BlockingEntityFetchService(
    val msgApi: MessageApi,
    val ctx: ActorContext
) {

  fun getEntity(entityId: Long): Entity? {
    val entityFuture = CompletableFuture<Entity>()
    awaitEntityResponse(
        messageApi = msgApi,
        ctx = ctx,
        entityId = entityId
    ) {
      entityFuture.complete(it)
    }

    return try {
      entityFuture.get(ENTITY_RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch(e: TimeoutException) {
      // timeout
      null
    }
  }

  companion object {
    private const val ENTITY_RESPONSE_TIMEOUT_MS = 1000L
  }
}

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
