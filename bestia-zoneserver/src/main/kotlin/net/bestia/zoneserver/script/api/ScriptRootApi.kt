package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
import java.lang.IllegalArgumentException

private val LOG = KotlinLogging.logger { }

/**
 * Global script API used by all scripts in the Bestia system to interact with
 * the Behemoth server.
 *
 * @author Thomas Felix
 */
class ScriptRootApi(
    private val idGeneratorService: IdGenerator,
    private val mobFactory: MobFactory,
    private val scriptContext: ScriptRootContext
) {

  fun info(text: String) {
    LOG.info { text }
  }

  fun debug(text: String) {
    LOG.debug { text }
  }

  fun findEntity(entityId: Long): EntityApi {
    if (entityId <= 0L) {
      throw IllegalArgumentException("Entity ID can not be null. This is probably a wrong call.")
    }
    LOG.debug { "findEntity: $entityId" }

    val ctx = EntityContext(entityId)
    scriptContext.entityContexts.add(ctx)

    return EntityApi(ctx)
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

    val ctx = EntityContext(entity.id)
    scriptContext.entityContexts.add(ctx)
    scriptContext.newEntities.add(entity)

    return EntityApi(ctx)
  }

  fun newEntity(): EntityApi {
    val entityId = idGeneratorService.newId()
    scriptContext.newEntities.add(Entity(entityId))
    val ctx = EntityContext(entityId)

    return EntityApi(ctx)
  }
}
