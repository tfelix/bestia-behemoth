package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.component.ComponentEnvelope
import net.bestia.zoneserver.entity.component.PositionComponent
import java.lang.IllegalStateException

private val LOG = KotlinLogging.logger { }

data class SetPositionToCommand(
    val entityId: Long,
    val position: Vec3
) : EntityCommand {
  override fun toEntityEnvelope(): EntityEnvelope {
    return EntityEnvelope(
        entityId = entityId,
        content = ComponentEnvelope(
            componentType = PositionComponent::class.java,
            content = this
        )
    )
  }
}

class EntityApi(
    val entityId: Long,
    private val commands: MutableList<EntityCommand>
) {

  fun setPosition(x: Long, y: Long, z: Long): EntityApi {
    commands.add(SetPositionToCommand(entityId, Vec3(x, y, z)))

    return this
  }

  fun getPosition(): Vec3 {
    throw IllegalStateException("not implemented")
  }

  fun condition(): EntityConditionApi {
    return EntityConditionApi(entityId = entityId, commands = commands)
  }

  fun script(): ScriptApi {

    return ScriptApi(entityId = entityId, commands = commands)
  }
}