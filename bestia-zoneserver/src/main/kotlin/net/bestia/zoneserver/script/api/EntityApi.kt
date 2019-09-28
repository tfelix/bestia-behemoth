package net.bestia.zoneserver.script.api

import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.chat.PositionToMessage

class EntityApi(
    private val entityContext: EntityContext
) {
  fun position(x: Long, y: Long, z: Long): EntityApi {
    entityContext.position = PositionToMessage(Vec3(x, y, z))
    return this
  }

  fun condition(): EntityConditionApi {
    val conditionCtx = EntityConditionContext(entityContext.entityId)
    entityContext.condition = conditionCtx

    return EntityConditionApi(conditionCtx)
  }

  fun script(): ScriptApi {
    val ctx = ScriptContext(entityContext.entityId)
    entityContext.script = ctx

    return ScriptApi(ctx)
  }
}