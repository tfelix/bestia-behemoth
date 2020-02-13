package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.model.geometry.Shape
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.AddComponentCommand
import net.bestia.zoneserver.entity.component.LivetimeComponent
import net.bestia.zoneserver.entity.component.VisualComponent
import java.time.Instant

private val LOG = KotlinLogging.logger { }

data class SetPositionToCommand(
    override val entityId: Long,
    val position: Vec3
) : EntityMessage

class EntityApi(
    private val entityId: Long,
    private val scriptName: String,
    private val commands: MutableList<EntityMessage>
) {

  fun calculateDamage(): EntityApi {

    return this
  }

  fun setPosition(pos: Vec3): EntityApi {
    setPosition(pos.x, pos.y, pos.z)

    return this
  }

  fun setPosition(x: Long, y: Long, z: Long): EntityApi {
    LOG.debug { "${scriptName}: setPosition($x: Long, $y: Long, $z: Long)" }
    commands.add(SetPositionToCommand(entityId, Vec3(x, y, z)))

    return this
  }

  fun setStatusValuesFrom(entityId: Long): EntityApi {
    LOG.debug { "${scriptName}: setStatusValuesFrom($entityId: Long)" }
    // FIXME Implement me

    return this
  }

  fun getPosition(): Vec3 {
    throw IllegalStateException("not implemented")
  }

  fun setVisual(visual: String): EntityApi {
    LOG.debug { "${scriptName}: setVisual($visual: String)" }
    require(visual.isNotEmpty()) { "visual must not be empty" }

    val visualComponent = VisualComponent(
        entityId = entityId,
        mesh = visual,
        isVisible = true
    )

    commands.add(AddComponentCommand(visualComponent))

    return this
  }

  fun setShape(shape: Shape): EntityApi {
    LOG.debug { "${scriptName}: setShape($shape: Shape)" }
    // FIXME Implement me

    return this
  }

  fun setLivetime(durationMs: Long): EntityApi {
    LOG.debug { "${scriptName}: setLivetime($durationMs: Long)" }
    require(durationMs > 0) { "durationMs must be bigger then 0" }

    val livetimeComponent = LivetimeComponent(entityId = entityId, killOn = Instant.now().plusMillis(durationMs))
    val addComponentCommand = AddComponentCommand(livetimeComponent)

    commands.add(addComponentCommand)

    return this
  }

  fun conditionApi(): EntityConditionApi {
    return EntityConditionApi(entityId = entityId, commands = commands)
  }

  fun scriptApi(): ScriptApi {
    return ScriptApi(entityId = entityId, commands = commands)
  }
}