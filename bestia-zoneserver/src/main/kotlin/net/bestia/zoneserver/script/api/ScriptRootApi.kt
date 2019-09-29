package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.model.geometry.Rect
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
    private val idGeneratorService: IdGenerator,
    private val rootCtx: ScriptRootContext,
    private val mobFactory: MobFactory,
    private val entityCollisionService: EntityCollisionService
) {

  fun info(text: String) {
    LOG.info { "${rootCtx.scriptName}: $text" }
  }

  fun debug(text: String) {
    LOG.debug { "${rootCtx.scriptName}: $text" }
  }

  fun findEntity(entityId: Long): EntityApi {
    require(entityId > 0L) { "Entity ID can not be null. This is probably a wrong call." }
    LOG.debug { "${rootCtx.scriptName}: findEntity($entityId)" }

    val ctx = EntityContext(entityId)
    rootCtx.entityContexts.add(ctx)

    return EntityApi(entityContext = ctx, mobFactory = mobFactory, rootCtx = rootCtx)
  }

  fun findEntitiesBbox(x1: Long, y1: Long, z1: Long, x2: Long, y2: Long, z2: Long): Array<EntityApi> {
    LOG.debug { "${rootCtx.scriptName}: findEntitiesBbox($x1: Long, $y1: Long, $z1: Long, $x2: Long, $y2: Long, $z2: Long)" }

    val rect = Rect(x1, y1, z1, x2, y2, z2)
    val entities = entityCollisionService.getAllCollidingEntityIds(rect)

    return entities.map {
      EntityContext(it)
    }.map {
      rootCtx.entityContexts.add(it)
      EntityApi(entityContext = it, mobFactory = mobFactory, rootCtx = rootCtx)
    }.toTypedArray()
  }

  fun newEntity(): EntityApi {
    val entityId = idGeneratorService.newId()
    rootCtx.newEntities.add(Entity(entityId))
    val ctx = EntityContext(entityId)

    return EntityApi(entityContext = ctx, mobFactory = mobFactory, rootCtx = rootCtx)
  }
}
