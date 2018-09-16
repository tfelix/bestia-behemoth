package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.PositionComponent

private val LOG = KotlinLogging.logger {  }

class EntityApi(
        private val rootApi: ScriptRootApi,
        private val entityId: Long,
        private val entityService: EntityService
) : ScriptChildApi {

  override fun and(): ScriptRootApi {
    return rootApi
  }

  fun position(x: Long, y: Long): EntityApi {
    val posComp = entityService.getComponentOrCreate(entityId, PositionComponent::class.java)
    posComp.setPosition(x, y)
    entityService.updateComponent(posComp)
    return this
  }

  fun script() : ScriptApi {
    return ScriptApi(rootApi, entityId, entityService)
  }
}