package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.chat.PositionToMessage
import net.bestia.zoneserver.entity.factory.MobFactory
import java.lang.IllegalStateException

private val LOG = KotlinLogging.logger { }

class EntityApi(
    private val entityContext: EntityContext,
    private val mobFactory: MobFactory,
    private val rootCtx: ScriptRootContext
) {

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
    rootCtx.entityContexts.add(ctx)
    rootCtx.newEntities.add(entity)

    return this
  }

  fun setPosition(x: Long, y: Long, z: Long): EntityApi {
    entityContext.position = PositionToMessage(Vec3(x, y, z))
    return this
  }

  fun getPosition(): Vec3 {
    throw IllegalStateException("not implemented")
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