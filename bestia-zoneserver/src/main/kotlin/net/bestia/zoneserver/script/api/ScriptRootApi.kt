package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.factory.EntityFactory
import net.bestia.zoneserver.entity.factory.MobBlueprint
import org.springframework.stereotype.Service
import java.lang.IllegalArgumentException

private val LOG = KotlinLogging.logger { }

/**
 * Global script API used by all scripts in the bestia system to interact with
 * the behemoth server.
 *
 * @author Thomas Felix
 */
@Service
class ScriptRootApi(
    private val entityService: EntityService,
    private val entityFactory: EntityFactory
) {
  fun info(text: String) {
    LOG.info { text }
  }

  fun debug(text: String) {
    LOG.debug { text }
  }

  fun entity(entityId: Long): EntityApi {
    if (entityId == 0L) {
      throw IllegalArgumentException("Entity ID can not be null. This is probably a wrong call.")
    }
    LOG.debug { "Looking up entity: $entityId" }
    return EntityApi(
        entityId = entityId,
        entityService = entityService,
        rootApi = this)
  }

  fun entity(mobName: String, position: Point): EntityApi {
    LOG.debug { "Creating mob entity: $mobName" }
    val mobBlueprint = MobBlueprint(mobName, position)
    val entity = entityFactory.build(mobBlueprint)
    return EntityApi(
        entityId = entity.id,
        entityService = entityService,
        rootApi = this
    )
  }

  fun entity(): EntityApi {
    val entity = entityService.newEntity()
    return EntityApi(
        entityId = entity.id,
        entityService = entityService,
        rootApi = this
    )
  }
}
