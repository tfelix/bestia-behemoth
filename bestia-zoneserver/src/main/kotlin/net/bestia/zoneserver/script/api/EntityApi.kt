package net.bestia.zoneserver.script.api

import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.model.geometry.Shape
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.entity.EntityRequestService
import net.bestia.zoneserver.actor.entity.component.SetPositionToAbsolute
import net.bestia.zoneserver.actor.entity.component.UpdateComponent
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.*
import java.time.Instant

private val LOG = KotlinLogging.logger { }

class EntityApi(
    private val entityId: Long,
    private val scriptName: String,
    private val commands: MutableList<EntityMessage>,
    private val entityRequestService: EntityRequestService
) {

  private val entity: Entity by lazy {
    entityRequestService.requestEntity(entityId)
  }

  fun calculateDamage(): EntityApi {

    return this
  }

  fun setPosition(pos: Vec3): EntityApi {
    setPosition(pos.x, pos.y, pos.z)

    return this
  }

  fun setPosition(x: Long, y: Long, z: Long): EntityApi {
    LOG.trace { "${scriptName}: setPosition($x: Long, $y: Long, $z: Long)" }
    commands.add(SetPositionToAbsolute(entityId, Vec3(x, y, z)))

    return this
  }

  fun getPosition(): Vec3 {
    LOG.trace { "${scriptName}: getPosition()" }
    return entity.getComponent(PositionComponent::class.java).position
  }

  fun getShape(): Shape {
    LOG.trace { "${scriptName}: getShape()" }
    return entity.getComponent(PositionComponent::class.java).shape
  }

  fun setShape(shape: Shape): EntityApi {
    LOG.trace { "${scriptName}: setShape($shape: Shape)" }
    // FIXME Implement me

    return this
  }

  fun setVisual(visual: String): EntityApi {
    LOG.trace { "${scriptName}: setVisual($visual: String)" }
    require(visual.isNotEmpty()) { "visual must not be empty" }

    val visualComponent = VisualComponent(
        entityId = entityId,
        mesh = visual,
        isVisible = true
    )

    commands.add(UpdateComponent(visualComponent))

    return this
  }

  fun copyStatusValuesFrom(copyEntityId: Long): EntityApi {
    LOG.trace { "${scriptName}: copyStatusValuesFrom($copyEntityId: Long)" }
    val entity = entityRequestService.requestEntity(copyEntityId)
    val statusComp = entity.getComponent(StatusComponent::class.java)

    commands.add(UpdateComponent(statusComp))

    return this
  }

  fun setLivetime(durationMs: Long): EntityApi {
    LOG.trace { "${scriptName}: setLivetime($durationMs: Long)" }
    require(durationMs > 0) { "durationMs must be bigger then 0" }

    val livetimeComponent = LivetimeComponent(entityId = entityId, killOn = Instant.now().plusMillis(durationMs))
    val addComponentCommand = UpdateComponent(livetimeComponent)

    commands.add(addComponentCommand)

    return this
  }

  fun condition(): EntityConditionApi {
    return EntityConditionApi(entityId = entityId, commands = commands)
  }

  fun script(): ScriptApi {
    val scriptComponent = ScriptComponent(
        entityId = entityId
    )

    commands.add(UpdateComponent(scriptComponent))

    return ScriptApi(entityId = entityId, commands = commands, scriptKey = scriptName)
  }
}