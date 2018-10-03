package net.bestia.zoneserver.script.api

import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.PositionComponent

class EntityApi(
    private val rootApi: ScriptRootApi,
    private val entityId: Long,
    private val entityService: EntityService
) : ScriptChildApi {

  override fun and(): ScriptRootApi {
    return rootApi
  }

  fun position(x: Long, y: Long): EntityApi {
    val posComp = PositionComponent(entityId)
    posComp.position = Point(x, y)
    entityService.updateComponent(posComp)
    return this
  }

  fun script(): ScriptApi {
    return ScriptApi(rootApi, entityId, entityService)
  }
}