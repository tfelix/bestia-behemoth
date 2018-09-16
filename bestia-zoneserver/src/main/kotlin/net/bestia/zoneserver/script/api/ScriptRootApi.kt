package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.zoneserver.entity.EntityService
import net.bestia.entity.factory.MobFactory
import org.springframework.stereotype.Service

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
        private val mobFactory: MobFactory
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

  fun entity(blueprint: String = "") : EntityApi {
    val entity = mobFactory.build(blueprint, 0, 0)
    return EntityApi(
            entityId = entity.id,
            entityService = entityService,
            rootApi = this)
  }

  fun entity() : EntityApi {
    val entity = entityService.newEntity()
    return EntityApi(
            entityId = entity.id,
            entityService = entityService,
            rootApi = this)
  }
}
