package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.entity.EntityService

private val LOG = KotlinLogging.logger { }

/**
 * Global script API used by all scripts in the bestia system to interact with
 * the behemoth server.
 *
 * @author Thomas Felix
 */
class ScriptRootApi(
        private val entityService: EntityService
) {
  fun info(text: String) {
    LOG.info { text }
  }

  fun debug(text: String) {
    LOG.debug { text }
  }

  fun entity(entityId: Long): EntityApi {
    LOG.debug { "Looking up entity: $entityId" }
    return EntityApi(
            entityId = entityId,
            entityService = entityService,
            rootApi = this)
  }

  fun entities(x: Long, y: Long, w: Long, h: Long) {

  }

  fun entity(blueprint: String = "") : EntityApi {
    LOG.info { "entity:String" }
    val entityId = 1337L
    return EntityApi(
            entityId = entityId,
            entityService = entityService,
            rootApi = this)
  }
}
